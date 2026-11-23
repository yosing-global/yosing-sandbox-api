package net.yosing.sandbox.api.application.port;

import java.util.ArrayList;
import java.util.List;

import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.InMemoryUserRepository;
import net.yosing.sandbox.api.domain.model.user.User;

/**
 * テスト用のログインの確定の保存（フェイク）。メモリ上のユーザーの保存先を更新し、作ったセッションを覚える。
 *
 * <p>本物と同じく、jti が読んだときと同じことと、一致したステップが最後に使ったステップより後であることを条件にする。
 */
public class FakeLoginCompletionStore implements LoginCompletionStore {

	private final InMemoryUserRepository users;

	private final List<Session> sessions = new ArrayList<>();

	private boolean rejectNext;

	public FakeLoginCompletionStore(InMemoryUserRepository users) {
		this.users = users;
	}

	@Override
	public boolean complete(User current, User verified, Session session) {
		if (this.rejectNext) {
			this.rejectNext = false;
			return false;
		}
		User stored = this.users.findById(current.id()).orElseThrow();
		if (!stored.loginTokenId().equals(current.loginTokenId())
				|| !verified.lastUsedTotpStep().isAfter(stored.lastUsedTotpStep())) {
			return false;
		}
		this.users.replace(verified);
		this.sessions.add(session);
		return true;
	}

	/**
	 * 次の保存を、条件に合わなかったことにする（同じコードが先に使われた。テストの準備）。
	 */
	public void rejectNext() {
		this.rejectNext = true;
	}

	/**
	 * 作ったセッション（テストの確認用）。
	 */
	public List<Session> sessions() {
		return List.copyOf(this.sessions);
	}

}
