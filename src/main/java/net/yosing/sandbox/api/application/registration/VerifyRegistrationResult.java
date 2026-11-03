package net.yosing.sandbox.api.application.registration;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.session.SessionId;

/**
 * 登録の確定の結果。
 *
 * @param sessionId セッション ID（クライアントに返す元の値）
 * @param sessionExpiresAt セッションの期限
 */
public record VerifyRegistrationResult(SessionId sessionId, Instant sessionExpiresAt) {
}
