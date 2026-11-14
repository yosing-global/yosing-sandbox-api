package net.yosing.sandbox.api.application.registration;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpauthUri;

/**
 * 登録の開始の結果。
 *
 * @param registrationId 登録 ID
 * @param otpauthUri 認証アプリの設定 URI（秘密情報を含む）
 * @param expiresAt 登録の期限
 */
public record StartRegistrationResult(RegistrationId registrationId, OtpauthUri otpauthUri, Instant expiresAt) {
}
