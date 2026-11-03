package net.yosing.sandbox.api.application.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import net.yosing.sandbox.api.application.port.FakeLoginTokenSigner;
import net.yosing.sandbox.api.application.port.FakePasswordHasher;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.InMemoryUserRepository;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * ログインの開始の流れ（成功・パスワードの失敗とロック・OTP のロック・ユーザーが無い・同時の試行）を確かめる。
 */
class StartLoginUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00.500Z");

	private static final Password PASSWORD = new Password("dummy correct horse battery");

	private static final Password WRONG_PASSWORD = new Password("dummy wrong horse battery");

	private final InMemoryUserRepository users = new InMemoryUserRepository();

	private final FakePasswordHasher hasher = new FakePasswordHasher();

	private final FakeLoginTokenSigner signer = new FakeLoginTokenSigner();

	private final StartLoginUseCase useCase = new StartLoginUseCase(users, hasher, signer,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final User user = addUser();

	/**
	 * パスワードが合えば、仮認証 JWT（sub は userId、期限は5分後）を返し、その jti をユーザーに保存する。
	 */
	@Test
	void issuesLoginToken() {
		StartLoginResult result = useCase.start(command(PASSWORD));

		Instant issuedAt = Instant.parse("2026-11-01T00:00:00Z");
		assertThat(result.expiresAt()).isEqualTo(issuedAt.plus(StartLoginUseCase.LOGIN_TOKEN_VALIDITY));
		LoginTokenClaims claims = signer.verify(result.loginToken(), NOW).orElseThrow();
		assertThat(claims.userId()).isEqualTo(user.id());
		assertThat(claims.issuedAt()).isEqualTo(issuedAt);
		assertThat(saved().loginTokenId()).hasValue(claims.tokenId());
		assertThat(saved().passwordFailureCount()).isZero();
	}

	/**
	 * パスワードが違えば失敗し、失敗回数を1増やす（jti は作らない）。
	 */
	@Test
	void countsWrongPassword() {
		assertThatExceptionOfType(InvalidCredentialsException.class)
			.isThrownBy(() -> useCase.start(command(WRONG_PASSWORD)));

		assertThat(saved().passwordFailureCount()).isEqualTo(1);
		assertThat(saved().loginTokenId()).isEmpty();
	}

	/**
	 * 5回続けて違えばロックし、その後は正しいパスワードでも同じ失敗にする（照合せず、失敗回数も増やさない）。
	 */
	@Test
	void locksAfterFiveFailures() {
		for (int attempt = 1; attempt <= User.MAX_PASSWORD_FAILURES; attempt++) {
			assertThatExceptionOfType(InvalidCredentialsException.class)
				.isThrownBy(() -> useCase.start(command(WRONG_PASSWORD)));
		}
		assertThat(saved().lockedUntil())
			.hasValue(Instant.parse("2026-11-01T00:00:00Z").plus(User.LOCK_DURATION));

		assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(() -> useCase.start(command(PASSWORD)));
		assertThat(saved().passwordFailureCount()).isEqualTo(User.MAX_PASSWORD_FAILURES);
		assertThat(saved().loginTokenId()).isEmpty();
	}

	/**
	 * 4回違えた後でも、パスワードが合えば失敗回数を0に戻す（続けての失敗だけを数える）。
	 */
	@Test
	void resetsFailuresOnSuccess() {
		for (int attempt = 1; attempt < User.MAX_PASSWORD_FAILURES; attempt++) {
			assertThatExceptionOfType(InvalidCredentialsException.class)
				.isThrownBy(() -> useCase.start(command(WRONG_PASSWORD)));
		}

		useCase.start(command(PASSWORD));

		assertThat(saved().passwordFailureCount()).isZero();
		assertThat(saved().lockedUntil()).isEmpty();
	}

	/**
	 * ログイン ID が無ければ、パスワードが違うときと同じ失敗にする（時間をかけるため、照合はする）。
	 */
	@Test
	void rejectsUnknownLoginId() {
		int matchesBefore = hasher.matchCount();

		assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(
				() -> useCase.start(new StartLoginCommand(LoginId.of("dummy_unknown"), PASSWORD)));

		assertThat(hasher.matchCount()).isEqualTo(matchesBefore + 1);
	}

	/**
	 * 同時のほかの試行に先を越されたら、パスワードが合っていても照合の結果を使わずに失敗する（上限を超えて照合しない）。
	 */
	@Test
	void rejectsWhenAnotherAttemptWins() {
		users.conflictOnNextPasswordAttempt();

		assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(() -> useCase.start(command(PASSWORD)));
		assertThat(saved().loginTokenId()).isEmpty();
	}

	/**
	 * パスワードが合っても、OTP の失敗回数は戻さない（ログインを始め直して、OTP を試し続けられないようにする）。
	 */
	@Test
	void keepsOtpFailureCount() {
		users.replace(withOtpState(3, Optional.empty()));

		useCase.start(command(PASSWORD));

		assertThat(saved().otpFailureCount()).isEqualTo(3);
	}

	/**
	 * OTP の失敗でロック中なら、正しいパスワードでも同じ失敗にする。ロックの期限が過ぎたら、失敗回数を0から数え直して始められる。
	 */
	@Test
	void rejectsWhileLockedByOtpFailures() {
		Instant lockedUntil = NOW.plusSeconds(60);
		users.replace(withOtpState(User.MAX_OTP_FAILURES, Optional.of(lockedUntil)));

		assertThatExceptionOfType(InvalidCredentialsException.class).isThrownBy(() -> useCase.start(command(PASSWORD)));

		users.replace(withOtpState(User.MAX_OTP_FAILURES, Optional.of(NOW)));
		useCase.start(command(PASSWORD));
		assertThat(saved().otpFailureCount()).isZero();
		assertThat(saved().lockedUntil()).isEmpty();
	}

	// OTP の失敗回数とロックの期限を変えたユーザー
	private User withOtpState(int otpFailureCount, Optional<Instant> lockedUntil) {
		return User.restore(user.id(), user.loginId(), user.passwordHash(), user.totpSecret(), user.lastUsedTotpStep(),
				0, otpFailureCount, lockedUntil, Optional.empty(), user.createdAt());
	}

	private StartLoginCommand command(Password password) {
		// 大文字で入力しても、小文字にそろえたログイン ID で探す
		return new StartLoginCommand(LoginId.of("Dummy_Alice"), password);
	}

	private User saved() {
		return users.user(user.id()).orElseThrow();
	}

	private User addUser() {
		User registered = User.register(UserId.generate(), LoginId.of("dummy_alice"), hasher.hash(PASSWORD),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), NOW.minusSeconds(3600));
		users.addUser(registered);
		return registered;
	}

}
