package net.yosing.sandbox.api.application.session;

import java.time.Clock;
import java.time.Instant;

import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.session.SessionRepository;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserRepository;
import org.springframework.stereotype.Service;

/**
 * ログイン中のユーザーの情報（GET /v1/me）。
 *
 * <ol>
 * <li>セッション ID のハッシュでセッションを取り出す。無い・期限切れなら 401</li>
 * <li>最終操作の日時と期限を延ばして保存する。同時にログアウトされていたら 401</li>
 * <li>ユーザーを取り出し、表示用の情報と、延ばした後のセッションの期限を返す（BFF が Cookie の期限をそろえる）</li>
 * </ol>
 */
@Service
public class GetCurrentUserUseCase {

	private final SessionRepository sessions;

	private final UserRepository users;

	private final Clock clock;

	public GetCurrentUserUseCase(SessionRepository sessions, UserRepository users, Clock clock) {
		this.sessions = sessions;
		this.users = users;
		this.clock = clock;
	}

	/**
	 * ログイン中のユーザーの情報を返す。
	 *
	 * @param sessionId クライアントから受け取ったセッション ID
	 * @return ログイン ID・登録した日時・延ばした後のセッションの期限
	 * @throws SessionInvalidException セッションが無い・期限切れ、またはユーザーが無い場合
	 */
	public CurrentUserResult get(SessionId sessionId) {
		Instant now = clock.instant();
		Session session = sessions.findByIdHash(sessionId.hash())
			.filter(found -> !found.isExpired(now))
			.orElseThrow(SessionInvalidException::new);
		Session touched = session.touch(now);
		if (!sessions.saveAccess(touched)) {
			throw new SessionInvalidException();
		}
		User user = users.findById(session.userId()).orElseThrow(SessionInvalidException::new);
		return new CurrentUserResult(user.loginId(), user.createdAt(), touched.expiresAt());
	}

}
