package net.yosing.sandbox.api.domain.model.session;

import java.util.Objects;

/**
 * 発行したセッション。セッション ID の元の値は、発行したときにしか分からない（DB にはハッシュだけを保存する）。
 *
 * @param sessionId セッション ID（クライアントに返す）
 * @param session セッション（DB に保存する）
 */
public record IssuedSession(SessionId sessionId, Session session) {

	public IssuedSession {
		Objects.requireNonNull(sessionId, "sessionId");
		Objects.requireNonNull(session, "session");
	}

}
