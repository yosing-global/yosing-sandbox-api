package net.yosing.sandbox.api.domain.model.user;

import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * ユーザーのロック（パスワード・OTP の試行の数え方・ロックの期限・成功したときの戻し方）と、仮認証・OTP の決まり
 * （jti の照合・試行の上限・成功したときのステップ）を確かめる。
 */
class UserTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	/**
	 * 試行を数えると失敗回数が1増え、上限の手前まではロックしない。
	 */
	@Test
	void countsAttemptWithoutLockBelowLimit() {
		User counted = user(3, Optional.empty()).countPasswordAttempt(NOW);

		assertThat(counted.passwordFailureCount()).isEqualTo(4);
		assertThat(counted.lockedUntil()).isEmpty();
		assertThat(counted.isLocked(NOW)).isFalse();
	}

	/**
	 * 5回目の試行を数えると、今から15分ロックする（期限の日時になったら解ける）。
	 */
	@Test
	void locksAtFifthAttempt() {
		User counted = user(4, Optional.empty()).countPasswordAttempt(NOW);

		assertThat(counted.passwordFailureCount()).isEqualTo(5);
		assertThat(counted.lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
		assertThat(counted.isLocked(NOW.plus(User.LOCK_DURATION).minusSeconds(1))).isTrue();
		assertThat(counted.isLocked(NOW.plus(User.LOCK_DURATION))).isFalse();
	}

	/**
	 * ロック中は試行を数えられない（照合させない）。
	 */
	@Test
	void rejectsAttemptWhileLocked() {
		User locked = user(5, Optional.of(NOW.plusSeconds(60)));

		assertThat(locked.isLocked(NOW)).isTrue();
		assertThatIllegalStateException().isThrownBy(() -> locked.countPasswordAttempt(NOW));
	}

	/**
	 * ロックの期限が過ぎた後の最初の試行では、パスワード・OTP の失敗回数を0に戻してから数え、ロックを解く。
	 */
	@Test
	void resetsCountsAfterLockExpires() {
		User counted = user(5, Optional.of(NOW)).countPasswordAttempt(NOW);

		assertThat(counted.passwordFailureCount()).isEqualTo(1);
		assertThat(counted.otpFailureCount()).isZero();
		assertThat(counted.lockedUntil()).isEmpty();
	}

	/**
	 * ロックしていなければ、パスワードの試行を数えても OTP の失敗回数は変えない。
	 */
	@Test
	void keepsOtpCountWhenCountingPasswordAttempt() {
		User counted = user(0, Optional.empty()).countPasswordAttempt(NOW);

		assertThat(counted.otpFailureCount()).isEqualTo(2);
	}

	/**
	 * パスワードが合ったら、パスワードの失敗回数を0に戻してロックを解き、新しい jti にする。
	 * OTP の失敗回数は戻さない（ログインを始め直して、OTP を試し続けられないようにする）。
	 */
	@Test
	void resetsPasswordStateWhenPasswordVerified() {
		User counted = user(4, Optional.empty()).countPasswordAttempt(NOW);

		User verified = counted.passwordVerified("dummy-jti");

		assertThat(verified.passwordFailureCount()).isZero();
		assertThat(verified.otpFailureCount()).isEqualTo(2);
		assertThat(verified.lockedUntil()).isEmpty();
		assertThat(verified.loginTokenId()).hasValue("dummy-jti");
	}

	/**
	 * 今の仮認証の jti とだけ一致する（仮認証が無ければ、どの jti とも一致しない）。
	 */
	@Test
	void matchesOnlyCurrentLoginToken() {
		User user = user(0, Optional.empty());

		assertThat(user.hasLoginToken("dummy-old-jti")).isTrue();
		assertThat(user.hasLoginToken("dummy-other-jti")).isFalse();
		assertThat(user.otpVerified(new TotpStep(124)).hasLoginToken("dummy-old-jti")).isFalse();
	}

	/**
	 * OTP の試行が5回に達したら上限で、残りの回数は0（試行の回数は2から）。
	 */
	@Test
	void exhaustsOtpAttemptsAtFive() {
		User user = user(0, Optional.empty());
		assertThat(user.otpAttemptsExhausted()).isFalse();
		assertThat(user.remainingOtpAttempts()).isEqualTo(3);

		User exhausted = User.restore(user.id(), user.loginId(), user.passwordHash(), user.totpSecret(),
				user.lastUsedTotpStep(), 0, User.MAX_OTP_FAILURES, Optional.empty(), user.loginTokenId(),
				user.createdAt());
		assertThat(exhausted.otpAttemptsExhausted()).isTrue();
		assertThat(exhausted.remainingOtpAttempts()).isZero();
	}

	/**
	 * OTP の失敗が上限に達したら、今から15分ロックして仮認証を終える（ロック中はパスワードの試行も数えられない）。
	 */
	@Test
	void locksForOtpFailures() {
		User user = user(0, Optional.empty());
		User exhausted = User.restore(user.id(), user.loginId(), user.passwordHash(), user.totpSecret(),
				user.lastUsedTotpStep(), 0, User.MAX_OTP_FAILURES, Optional.empty(), user.loginTokenId(),
				user.createdAt());

		User locked = exhausted.lockForOtpFailures(NOW);

		assertThat(locked.lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
		assertThat(locked.loginTokenId()).isEmpty();
		assertThat(locked.isLocked(NOW)).isTrue();
		assertThatIllegalStateException().isThrownBy(() -> locked.countPasswordAttempt(NOW));
	}

	/**
	 * OTP の失敗が上限に達していなければ、OTP のロックはできない。
	 */
	@Test
	void rejectsOtpLockBelowLimit() {
		assertThatIllegalStateException().isThrownBy(() -> user(0, Optional.empty()).lockForOtpFailures(NOW));
	}

	/**
	 * OTP が合ったら、一致したステップを最後に使ったステップにし、OTP の失敗回数を0に戻して仮認証を終える。
	 */
	@Test
	void recordsMatchedStepWhenOtpVerified() {
		User verified = user(0, Optional.empty()).otpVerified(new TotpStep(124));

		assertThat(verified.lastUsedTotpStep()).isEqualTo(new TotpStep(124));
		assertThat(verified.otpFailureCount()).isZero();
		assertThat(verified.loginTokenId()).isEmpty();
	}

	/**
	 * 最後に使ったステップ以前のステップでは、OTP が合ったことにできない（同じコードの再利用を防ぐ）。
	 */
	@Test
	void rejectsStepNotAfterLastUsed() {
		User user = user(0, Optional.empty());

		assertThatIllegalArgumentException().isThrownBy(() -> user.otpVerified(new TotpStep(123)));
	}

	private static User user(int passwordFailureCount, Optional<Instant> lockedUntil) {
		return User.restore(UserId.generate(), LoginId.of("dummy_alice"), new PasswordHash("{dummy}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), passwordFailureCount, 2, lockedUntil,
				Optional.of("dummy-old-jti"), NOW.minusSeconds(3600));
	}

}
