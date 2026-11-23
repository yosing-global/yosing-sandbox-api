package net.yosing.sandbox.api.presentation.login;

import java.time.Instant;

import net.yosing.sandbox.api.application.login.VerifyLoginResult;

/**
 * ログイン確定の応答（200 OK）
 *
 * @param sessionId セッション ID（BFF が Cookie に設定する）
 * @param sessionExpiresAt セッションの期限
 */
record VerifyLoginResponse(String sessionId, Instant sessionExpiresAt) {

	static VerifyLoginResponse from(VerifyLoginResult result) {
		return new VerifyLoginResponse(result.sessionId().value(), result.sessionExpiresAt());
	}

	@Override
	public String toString() {
		return "VerifyLoginResponse[sessionId=****, sessionExpiresAt=" + this.sessionExpiresAt + "]";
	}

}
