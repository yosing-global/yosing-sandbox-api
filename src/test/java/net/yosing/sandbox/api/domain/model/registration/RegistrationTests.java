package net.yosing.sandbox.api.domain.model.registration;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仮登録の始め方（期限・失敗回数）、期限切れ・OTP の失敗の上限の判定、本登録（ユーザーへの置き換え）を確かめる。
 */
class RegistrationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00.789Z");

	private final Registration registration = Registration.start(RegistrationId.generate(), UserId.generate(),
			LoginId.of("dummy_alice"), new PasswordHash("{bcrypt}dummy-hash"),
			new EncryptedTotpSecret(new byte[] { 1 }), NOW);

	/**
	 * 作成日時は秒にそろえ、期限はその10分後。OTP の失敗回数は0から始める。
	 */
	@Test
	void startsWithTenMinutesValidity() {
		assertThat(this.registration.createdAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(this.registration.expiresAt()).isEqualTo(Instant.parse("2026-11-01T00:10:00Z"));
		assertThat(this.registration.otpFailureCount()).isZero();
	}

	/**
	 * 期限の直前までは有効で、期限の日時になったら切れている。
	 */
	@Test
	void expiresWhenExpiresAtComes() {
		assertThat(this.registration.isExpired(this.registration.expiresAt().minusMillis(1))).isFalse();
		assertThat(this.registration.isExpired(this.registration.expiresAt())).isTrue();
	}

	/**
	 * OTP の失敗は5回が上限。残りの回数は、上限から失敗回数を引いたもの（0より小さくしない）。
	 */
	@Test
	void limitsOtpAttemptsToFive() {
		assertThat(withOtpFailures(4).otpAttemptsExhausted()).isFalse();
		assertThat(withOtpFailures(4).remainingOtpAttempts()).isEqualTo(1);
		assertThat(withOtpFailures(5).otpAttemptsExhausted()).isTrue();
		assertThat(withOtpFailures(6).remainingOtpAttempts()).isZero();
	}

	/**
	 * 本登録すると、仮登録のユーザーの ID・ログイン ID・パスワード・秘密情報を引き継ぎ、一致したステップを最後に使ったステップにする。
	 */
	@Test
	void completesToUser() {
		TotpStep step = new TotpStep(123);

		User user = this.registration.complete(step, NOW);

		assertThat(user.id()).isEqualTo(this.registration.userId());
		assertThat(user.loginId()).isEqualTo(this.registration.loginId());
		assertThat(user.passwordHash()).isEqualTo(this.registration.passwordHash());
		assertThat(user.totpSecret()).isEqualTo(this.registration.totpSecret());
		assertThat(user.lastUsedTotpStep()).isEqualTo(step);
		assertThat(user.createdAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(user.passwordFailureCount()).isZero();
		assertThat(user.lockedUntil()).isEmpty();
		assertThat(user.loginTokenId()).isEmpty();
	}

	private Registration withOtpFailures(int count) {
		return Registration.restore(this.registration.id(), this.registration.userId(), this.registration.loginId(),
				this.registration.passwordHash(), this.registration.totpSecret(), count, this.registration.createdAt(),
				this.registration.expiresAt());
	}

}
