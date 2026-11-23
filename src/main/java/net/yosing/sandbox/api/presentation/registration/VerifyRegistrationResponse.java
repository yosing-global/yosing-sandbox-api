package net.yosing.sandbox.api.presentation.registration;

import java.time.Instant;

import net.yosing.sandbox.api.application.registration.VerifyRegistrationResult;

/**
 * 登録確定の応答（201 Created）
 *
 * @param sessionId セッション ID（BFF が Cookie に設定する）
 * @param sessionExpiresAt セッションの期限
 */
record VerifyRegistrationResponse(String sessionId, Instant sessionExpiresAt) {

	static VerifyRegistrationResponse from(VerifyRegistrationResult result) {
		return new VerifyRegistrationResponse(result.sessionId().value(), result.sessionExpiresAt());
	}

	@Override
	public String toString() {
		return "VerifyRegistrationResponse[sessionId=****, sessionExpiresAt=" + this.sessionExpiresAt + "]";
	}

}
