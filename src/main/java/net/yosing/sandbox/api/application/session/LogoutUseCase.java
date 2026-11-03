package net.yosing.sandbox.api.application.session;

import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.session.SessionRepository;
import org.springframework.stereotype.Service;

/**
 * ログアウト（DELETE /v1/sessions/current）。
 *
 * <p>セッションを削除する。既に無い（期限切れ・ログアウト済み）ときも成功にする（何度呼んでも同じ結果にする）。
 */
@Service
public class LogoutUseCase {

	private final SessionRepository sessions;

	public LogoutUseCase(SessionRepository sessions) {
		this.sessions = sessions;
	}

	/**
	 * ログアウトする。
	 *
	 * @param sessionId クライアントから受け取ったセッション ID
	 */
	public void logout(SessionId sessionId) {
		sessions.delete(sessionId.hash());
	}

}
