package net.yosing.sandbox.api.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import org.junit.jupiter.api.Test;

/**
 * ユーザーのパスワードのロック（試行の数え方・ロックの期限・成功したときの戻し方）を確かめる。
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
		assertThat(counted.isPasswordLocked(NOW)).isFalse();
	}

	/**
	 * 5回目の試行を数えると、今から15分ロックする（期限の日時になったら解ける）。
	 */
	@Test
	void locksAtFifthAttempt() {
		User counted = user(4, Optional.empty()).countPasswordAttempt(NOW);

		assertThat(counted.passwordFailureCount()).isEqualTo(5);
		assertThat(counted.lockedUntil()).hasValue(NOW.plus(User.PASSWORD_LOCK_DURATION));
		assertThat(counted.isPasswordLocked(NOW.plus(User.PASSWORD_LOCK_DURATION).minusSeconds(1))).isTrue();
		assertThat(counted.isPasswordLocked(NOW.plus(User.PASSWORD_LOCK_DURATION))).isFalse();
	}

	/**
	 * ロック中は試行を数えられない（照合させない）。
	 */
	@Test
	void rejectsAttemptWhileLocked() {
		User locked = user(5, Optional.of(NOW.plusSeconds(60)));

		assertThat(locked.isPasswordLocked(NOW)).isTrue();
		assertThatIllegalStateException().isThrownBy(() -> locked.countPasswordAttempt(NOW));
	}

	/**
	 * ロックの期限が過ぎた後の最初の試行では、失敗回数を0に戻してから数え、ロックを解く。
	 */
	@Test
	void resetsCountAfterLockExpires() {
		User counted = user(5, Optional.of(NOW)).countPasswordAttempt(NOW);

		assertThat(counted.passwordFailureCount()).isEqualTo(1);
		assertThat(counted.lockedUntil()).isEmpty();
	}

	/**
	 * パスワードが合ったら、失敗回数（パスワード・OTP）を0に戻してロックを解き、新しい jti にする。
	 */
	@Test
	void resetsStateWhenPasswordVerified() {
		User counted = user(4, Optional.empty()).countPasswordAttempt(NOW);

		User verified = counted.passwordVerified("dummy-jti");

		assertThat(verified.passwordFailureCount()).isZero();
		assertThat(verified.otpFailureCount()).isZero();
		assertThat(verified.lockedUntil()).isEmpty();
		assertThat(verified.loginTokenId()).hasValue("dummy-jti");
	}

	private static User user(int passwordFailureCount, Optional<Instant> lockedUntil) {
		return User.restore(UserId.generate(), LoginId.of("dummy_alice"), new PasswordHash("{dummy}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), passwordFailureCount, 2, lockedUntil,
				Optional.of("dummy-old-jti"), NOW.minusSeconds(3600));
	}

}
