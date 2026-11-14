package net.yosing.sandbox.api.application.login;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.session.SessionId;

/**
 * ログインの確定の結果。
 *
 * @param sessionId セッション ID（クライアントに返す元の値）
 * @param sessionExpiresAt セッションの期限
 */
public record VerifyLoginResult(SessionId sessionId, Instant sessionExpiresAt) {
}
