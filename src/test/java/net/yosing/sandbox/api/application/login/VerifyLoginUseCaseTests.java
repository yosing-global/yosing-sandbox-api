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
 * ログインの確定の流れ（成功・仮認証 JWT の照合・OTP の失敗とアカウントのロック・同じコードの再利用）を確かめる。
 */
class VerifyLoginUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:05:00Z");

	private static final TotpSecret SECRET = new TotpSecret("dummy-totp-secret-20".getBytes(StandardCharsets.US_ASCII));

	private static final String TOKEN_ID = "dummy-jti";

	private final InMemoryUserRepository users = new InMemoryUserRepository();

	private final FakeLoginCompletionStore completionStore = new FakeLoginCompletionStore(users);

	private final FakeLoginTokenSigner signer = new FakeLoginTokenSigner();

	private final FakeTotpSecretEncryptor encryptor = new FakeTotpSecretEncryptor();

	private final TotpVerifier totpVerifier = new TotpVerifier();

	private final VerifyLoginUseCase useCase = new VerifyLoginUseCase(signer, users, completionStore, encryptor,
			totpVerifier, Clock.fixed(NOW, ZoneOffset.UTC));

	private final User user = addUser(TotpStep.at(NOW).plus(-5));

	private final String loginToken = signToken(user.id(), TOKEN_ID);

	private final OtpCode validCode = totpVerifier.generate(SECRET, TotpStep.at(NOW));

	private final OtpCode wrongCode = codeOutsideWindow();

	/**
	 * 正しいコードなら、セッションを発行してセッション ID と期限を返し、一致したステップを保存して仮認証を終える。
	 */
	@Test
	void completesLogin() {
		VerifyLoginResult result = useCase.verify(new VerifyLoginCommand(loginToken, validCode));

		assertThat(completionStore.sessions()).hasSize(1);
		Session session = completionStore.sessions().get(0);
		assertThat(session.userId()).isEqualTo(user.id());
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
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand("dummy-unknown-token", validCode)));

		useCase.verify(new VerifyLoginCommand(loginToken, validCode));
		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, validCode)));
	}

	/**
	 * jti が今の仮認証のものでなければ（新しいログインの開始で置き換わった）、仮認証が無効の失敗にする（試行も数えない）。
	 */
	@Test
	void rejectsReplacedLoginToken() {
		String oldToken = signToken(user.id(), "dummy-old-jti");

		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(oldToken, validCode)));
		assertThat(saved().otpFailureCount()).isZero();
	}

	/**
	 * コードが違えば、試行を数えて残りの回数を付けて失敗する（仮認証は続く）。
	 */
	@Test
	void countsWrongCode() {
		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, wrongCode)))
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
				.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, wrongCode)));
		}

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, wrongCode)));
		assertThat(saved().lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
		assertThat(saved().loginTokenId()).isEmpty();
		assertThatExceptionOfType(LoginTokenInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, validCode)));
	}

	/**
	 * OTP の失敗は仮認証をまたいで数える（4回失敗してからログインを始め直しても、次の失敗でロックする）。
	 */
	@Test
	void countsFailuresAcrossLoginTokens() {
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, wrongCode)));
		}
		users.replace(saved().passwordVerified("dummy-new-jti"));
		String newToken = signToken(user.id(), "dummy-new-jti");

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(newToken, wrongCode)));
		assertThat(saved().lockedUntil()).isPresent();
	}

	/**
	 * 失敗の後でも、コードが合えば OTP の失敗回数を0に戻す（続けての失敗だけを数える）。
	 */
	@Test
	void resetsFailuresOnSuccess() {
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, wrongCode)));
		}

		useCase.verify(new VerifyLoginCommand(loginToken, validCode));

		assertThat(saved().otpFailureCount()).isZero();
	}

	/**
	 * 失敗回数が上限なのにロックできていなかったら（その間に新しい仮認証が始まった等）、照合せずにロックして上限の失敗にする。
	 */
	@Test
	void locksWhenAlreadyExhausted() {
		users.replace(
				User.restore(user.id(), user.loginId(), user.passwordHash(), user.totpSecret(), user.lastUsedTotpStep(),
						0, User.MAX_OTP_FAILURES, Optional.empty(), Optional.of(TOKEN_ID), user.createdAt()));

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, validCode)));
		assertThat(saved().lockedUntil()).isPresent();
		assertThat(completionStore.sessions()).isEmpty();
	}

	/**
	 * 最後に使ったステップのコード（登録・前のログインで使ったもの）は、合っていても OTP の失敗にする。
	 */
	@Test
	void rejectsCodeOfLastUsedStep() {
		User usedNow = addUser(TotpStep.at(NOW));
		String token = signToken(usedNow.id(), TOKEN_ID);

		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(token, validCode)));
	}

	/**
	 * 保存の条件に合わなければ（同じコードが同時に使われた）、OTP の失敗にする（セッションは作らない）。
	 */
	@Test
	void rejectsWhenCodeUsedConcurrently() {
		completionStore.rejectNext();

		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> useCase.verify(new VerifyLoginCommand(loginToken, validCode)))
			.returns(4, OtpInvalidException::remainingAttempts);
		assertThat(completionStore.sessions()).isEmpty();
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode codeOutsideWindow() {
		TotpStep step = TotpStep.at(NOW);
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter(code -> totpVerifier.verify(SECRET, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private String signToken(UserId userId, String tokenId) {
		Instant issuedAt = NOW.minusSeconds(60);
		return signer.sign(
				new LoginTokenClaims(userId, tokenId, issuedAt, issuedAt.plus(StartLoginUseCase.LOGIN_TOKEN_VALIDITY)));
	}

	private User saved() {
		return users.findById(user.id()).orElseThrow();
	}

	// パスワードが合って、仮認証（jti）が始まっているユーザー
	private User addUser(TotpStep lastUsedStep) {
		UserId userId = UserId.generate();
		User registered = User.register(userId, LoginId.of("dummy_alice"), new PasswordHash("{dummy}dummy-hash"),
				encryptor.encrypt(SECRET, userId), lastUsedStep, NOW.minusSeconds(3600));
		User started = registered.passwordVerified(TOKEN_ID);
		users.addUser(started);
		return started;
	}

}
