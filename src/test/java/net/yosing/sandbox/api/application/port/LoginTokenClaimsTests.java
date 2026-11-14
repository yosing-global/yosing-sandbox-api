package net.yosing.sandbox.api.application.port;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * 仮認証 JWT の中身として正しくない値を受け付けないことを確かめる。
 */
class LoginTokenClaimsTests {

	private static final Instant ISSUED_AT = Instant.parse("2026-11-01T00:00:00Z");

	/**
	 * jti が空のものは作れない（USER の値と照合できないため）。
	 */
	@Test
	void rejectsBlankTokenId() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoginTokenClaims(UserId.generate(), " ", ISSUED_AT, ISSUED_AT.plusSeconds(300)));
	}

	/**
	 * 有効期限が発行日時より後でないものは作れない。
	 */
	@Test
	void rejectsExpiresAtNotAfterIssuedAt() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> new LoginTokenClaims(UserId.generate(), "dummy-token-id", ISSUED_AT, ISSUED_AT));
	}

}
