package net.yosing.sandbox.api.domain.model.session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * テスト用の、メモリ上のセッションの保存先（フェイク）。
 *
 * <p>延長の保存は、本物と同じく、セッションがあることを条件にする。
 */
public class InMemorySessionRepository implements SessionRepository {

	private final Map<SessionIdHash, Session> sessions = new HashMap<>();

	/**
	 * セッションを足す（テストの準備）。
	 */
	public void add(Session session) {
		sessions.put(session.idHash(), session);
	}

	@Override
	public Optional<Session> findByIdHash(SessionIdHash idHash) {
		return Optional.ofNullable(sessions.get(idHash));
	}

	@Override
	public boolean saveAccess(Session touched) {
		if (!sessions.containsKey(touched.idHash())) {
			return false;
		}
		sessions.put(touched.idHash(), touched);
		return true;
	}

	@Override
	public void delete(SessionIdHash idHash) {
		sessions.remove(idHash);
	}

}
