package net.yosing.sandbox.api.application.port;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * テスト用のテストのユーザーの掃除の保存先（フェイク）。メモリ上のユーザーから探し、消す。
 *
 * <p>本物と同じく、ログイン ID が先頭に合うユーザーだけを返し、{@link TestUserCleanupStore#TEST_LOGIN_ID_PREFIX} で
 * 始まらないものは探さない・消さない。探した回数を覚える（許されない環境で、探していないことを確かめるため）。
 */
public class FakeTestUserCleanupStore implements TestUserCleanupStore {

	private final List<TestUser> users = new ArrayList<>();

	private final Set<UserId> rejected = new HashSet<>();

	private int findCount;

	@Override
	public List<TestUser> findByLoginIdPrefix(String loginIdPrefix) {
		if (!loginIdPrefix.startsWith(TEST_LOGIN_ID_PREFIX)) {
			throw new IllegalArgumentException("テストのユーザーのログイン ID ではない");
		}
		findCount++;
		return users.stream().filter(user -> user.loginId().value().startsWith(loginIdPrefix)).toList();
	}

	@Override
	public boolean delete(TestUser user) {
		if (!user.loginId().value().startsWith(TEST_LOGIN_ID_PREFIX)) {
			throw new IllegalArgumentException("テストのユーザーのログイン ID ではない");
		}
		if (rejected.contains(user.id())) {
			return false;
		}
		return users.remove(user);
	}

	/**
	 * ユーザーを置く（テストの準備）。
	 */
	public void add(TestUser user) {
		users.add(user);
	}

	/**
	 * このユーザーの削除を、条件に合わなかったことにする（既に消えた等。テストの準備）。
	 */
	public void rejectDelete(UserId id) {
		rejected.add(id);
	}

	/**
	 * 残っているユーザー。
	 */
	public List<TestUser> users() {
		return List.copyOf(users);
	}

	/**
	 * 探した回数。
	 */
	public int findCount() {
		return findCount;
	}

}
