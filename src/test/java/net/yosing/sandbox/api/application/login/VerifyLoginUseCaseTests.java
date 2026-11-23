package net.yosing.sandbox.api.application.login;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import net.yosing.sandbox.api.application.error.OtpAttemptsExceededException;
import net.yosing.sandbox.api.application.error.OtpInvalidException;
import net.yosing.sandbox.api.application.port.FakeLoginCompletionStore;
import net.yosing.sandbox.api.application.port.FakeLoginTokenSigner;
import net.yosing.sandbox.api.application.port.FakeTotpSecretEncryptor;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.InMemoryUserRepository;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.domain.service.TotpVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * ログイン確定の流れ（成功・仮認証 JWT の照合・OTP の失敗とアカウントのロック・同じコードの再利用）を確かめる。
 */
class VerifyLoginUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:05:00Z");

	private static final TotpSecret SECRET = new TotpSecret("dummy-totp-secret-20".getBytes(StandardCharsets.US_ASCII));

	private static final String TOKEN_ID = "dummy-jti";

	private final InMemoryUserRepository users = new InMemoryUserRepository();

	private final FakeLoginCompletionStore completionStore = new FakeLoginCompletionStore(this.users);

	private final FakeLoginTokenSigner signer = new FakeLoginTokenSigner();

	private final FakeTotpSecretEncryptor encryptor = new FakeTotpSecretEncryptor();

	private final TotpVerifier totpVerifier = new TotpVerifier();

	private final VerifyLoginUseCase useCase = new VerifyLoginUseCase(this.signer, this.users, this.completionStore,
			this.encryptor, this.totpVerifier, Clock.fixed(NOW, ZoneOffset.UTC));

	private final User user = addUser(TotpStep.at(NOW).plus(-5));

	private final String loginToken = signToken(this.user.id(), TOKEN_ID);

	private final OtpCode validCode = this.totpVerifier.generate(SECRET, TotpStep.at(NOW));

	private final OtpCode wrongCode = codeOutsideWindow();

	/**
	 * 正しいコードなら、セッションを発行してセッション ID と期限を返し、一致したステップを保存して仮認証を終える。
	 */
	@Test
	void completesLogin() {
		VerifyLoginResult result = this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode));

		assertThat(this.completionStore.sessions()).hasSize(1);
		Session session = this.completionStore.sessions().get(0);
		assertThat(session.userId()).isEqualTo(this.user.id());
		assertThat(session.idHash()).isEqualTo(result.sessionId().hash());
		assertThat(result.sessionExpiresAt()).isEqualTo(session.expiresAt());
		User saved = saved();
		assertThat(saved.lastUsedTotpStep()).isEqualTo(TotpStep.at(NOW));
		assertThat(saved.otpFailureCount()).isZero();
		assertThat(saved.loginTokenId()).isEmpty();
	}

	/**
	 * 署名・期限で無効な JWT、使い終わった仮認証の JWT は、仮認証が無効の失敗にする。
	 */
	@Test
	void rejectsInvalidOrUsedLoginToken() {
		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand("dummy-unknown-token", this.validCode)));

		this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode));
		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode)));
	}

	/**
	 * jti が今の仮認証のものでなければ（新しいログインの開始で置き換わった）、仮認証が無効の失敗にする（試行も数えない）。
	 */
	@Test
	void rejectsReplacedLoginToken() {
		String oldToken = signToken(this.user.id(), "dummy-old-jti");

		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(oldToken, this.validCode)));
		assertThat(saved().otpFailureCount()).isZero();
	}

	/**
	 * コードが違えば、試行を数えて残りの回数を付けて失敗する（仮認証は続く）。
	 */
	@Test
	void countsWrongCode() {
		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.wrongCode)))
			.returns(4, OtpInvalidException::remainingAttempts);
		assertThat(saved().otpFailureCount()).isEqualTo(1);
		assertThat(saved().loginTokenId()).hasValue(TOKEN_ID);
	}

	/**
	 * 5回目の失敗でアカウントを15分ロックして仮認証を終え、上限の失敗にする。その後は正しいコードでも仮認証が無効の失敗にする。
	 */
	@Test
	void locksAccountAtFifthFailure() {
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.wrongCode)));
		}

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.wrongCode)));
		assertThat(saved().lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
		assertThat(saved().loginTokenId()).isEmpty();
		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode)));
	}

	/**
	 * OTP の失敗は仮認証をまたいで数える（4回失敗してからログインを始め直しても、次の失敗でロックする）。
	 */
	@Test
	void countsFailuresAcrossLoginTokens() {
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.wrongCode)));
		}
		this.users.replace(saved().passwordVerified("dummy-new-jti"));
		String newToken = signToken(this.user.id(), "dummy-new-jti");

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(newToken, this.wrongCode)));
		assertThat(saved().lockedUntil()).isPresent();
	}

	/**
	 * 失敗の後でも、コードが合えば OTP の失敗回数を0に戻す（続けての失敗だけを数える）。
	 */
	@Test
	void resetsFailuresOnSuccess() {
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.wrongCode)));
		}

		this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode));

		assertThat(saved().otpFailureCount()).isZero();
	}

	/**
	 * 失敗回数が上限なのにロックできていなかったら（その間に新しい仮認証が始まった等）、照合せずにロックして上限の失敗にする。
	 */
	@Test
	void locksWhenAlreadyExhausted() {
		this.users.replace(User.restore(this.user.id(), this.user.loginId(), this.user.passwordHash(),
				this.user.totpSecret(), this.user.lastUsedTotpStep(), 0, User.MAX_OTP_FAILURES, Optional.empty(),
				Optional.of(TOKEN_ID), this.user.createdAt()));

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode)));
		assertThat(saved().lockedUntil()).isPresent();
		assertThat(this.completionStore.sessions()).isEmpty();
	}

	/**
	 * 最後に使ったステップのコード（登録・前のログインで使ったもの）は、合っていても OTP の失敗にする。
	 */
	@Test
	void rejectsCodeOfLastUsedStep() {
		User usedNow = addUser(TotpStep.at(NOW));
		String token = signToken(usedNow.id(), TOKEN_ID);

		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(token, this.validCode)));
	}

	/**
	 * 保存の条件に合わなければ（同じコードが同時に使われた）、OTP の失敗にする（セッションは作らない）。
	 */
	@Test
	void rejectsWhenCodeUsedConcurrently() {
		this.completionStore.rejectNext();

		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyLoginCommand(this.loginToken, this.validCode)))
			.returns(4, OtpInvalidException::remainingAttempts);
		assertThat(this.completionStore.sessions()).isEmpty();
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode codeOutsideWindow() {
		TotpStep step = TotpStep.at(NOW);
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter((code) -> this.totpVerifier.verify(SECRET, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private String signToken(UserId userId, String tokenId) {
		Instant issuedAt = NOW.minusSeconds(60);
		return this.signer.sign(
				new LoginTokenClaims(userId, tokenId, issuedAt, issuedAt.plus(StartLoginUseCase.LOGIN_TOKEN_VALIDITY)));
	}

	private User saved() {
		return this.users.findById(this.user.id()).orElseThrow();
	}

	// パスワードが合って、仮認証（jti）が始まっているユーザー
	private User addUser(TotpStep lastUsedStep) {
		UserId userId = UserId.generate();
		User registered = User.register(userId, LoginId.of("dummy_alice"), new PasswordHash("{dummy}dummy-hash"),
				this.encryptor.encrypt(SECRET, userId), lastUsedStep, NOW.minusSeconds(3600));
		User started = registered.passwordVerified(TOKEN_ID);
		this.users.addUser(started);
		return started;
	}

}
