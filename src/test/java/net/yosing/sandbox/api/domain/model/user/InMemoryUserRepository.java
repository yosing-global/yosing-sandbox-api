package net.yosing.sandbox.api.domain.model.user;

import java.util.HashSet;
import java.util.Set;

/**
 * テスト用の、メモリ上のユーザーの保存先（フェイク）。
 */
public class InMemoryUserRepository implements UserRepository {

	private final Set<LoginId> loginIds = new HashSet<>();

	/**
	 * 本登録済みのログイン ID を足す（テストの準備）。
	 */
	public void addLoginId(LoginId loginId) {
		loginIds.add(loginId);
	}

	@Override
	public boolean existsByLoginId(LoginId loginId) {
		return loginIds.contains(loginId);
	}

}
