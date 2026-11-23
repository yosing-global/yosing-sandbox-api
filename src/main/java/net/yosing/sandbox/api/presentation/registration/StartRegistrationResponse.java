package net.yosing.sandbox.api.presentation.registration;

import java.time.Instant;

import net.yosing.sandbox.api.application.registration.StartRegistrationResult;

/**
 * 登録の開始の応答（201 Created）。
 *
 * @param registrationId 登録 ID
 * @param otpauthUri 認証アプリの設定 URI（秘密情報を含む。BFF が QR コードにして表示する）
 * @param expiresAt 登録の期限
 */
record StartRegistrationResponse(String registrationId, String otpauthUri, Instant expiresAt) {

	static StartRegistrationResponse from(StartRegistrationResult result) {
		return new StartRegistrationResponse(result.registrationId().toString(), result.otpauthUri().value(),
				result.expiresAt());
	}

	@Override
	public String toString() {
		return "StartRegistrationResponse[registrationId=" + this.registrationId + ", otpauthUri=****, expiresAt="
				+ this.expiresAt + "]";
	}

}
