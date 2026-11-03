package net.yosing.sandbox.api.domain.model.registration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * 仮登録の始め方（期限・失敗回数）と、期限切れの判定を確かめる。
 */
class RegistrationTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00.789Z");

	private final Registration registration = Registration.start(RegistrationId.generate(), UserId.generate(),
			LoginId.of("dummy_alice"), new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }),
			NOW);

	/**
	 * 作成日時は秒にそろえ、期限はその10分後。OTP の失敗回数は0から始める。
	 */
	@Test
	void startsWithTenMinutesValidity() {
		assertThat(registration.createdAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(registration.expiresAt()).isEqualTo(Instant.parse("2026-11-01T00:10:00Z"));
		assertThat(registration.otpFailureCount()).isZero();
	}

	/**
	 * 期限の直前までは有効で、期限の日時になったら切れている。
	 */
	@Test
	void expiresWhenExpiresAtComes() {
		assertThat(registration.isExpired(registration.expiresAt().minusMillis(1))).isFalse();
		assertThat(registration.isExpired(registration.expiresAt())).isTrue();
	}

}
