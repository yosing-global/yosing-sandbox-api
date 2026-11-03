package net.yosing.sandbox.api.domain.model.user;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * テスト用の、メモリ上のユーザーの保存先（フェイク）。
 *
 * <p>パスワードの試行の保存は、本物と同じく、読んだときから失敗回数・ロックの期限が変わっていないことを条件にする。
 */
public class InMemoryUserRepository implements UserRepository {

	private final Set<LoginId> loginIds = new HashSet<>();

	private final Map<UserId, User> users = new HashMap<>();

	private boolean conflictOnNextPasswordAttempt;

	/**
	 * 本登録済みのログイン ID を足す（ユーザーは作らない。テストの準備）。
	 */
	public void addLoginId(LoginId loginId) {
		loginIds.add(loginId);
	}

	/**
	 * ユーザーとログイン ID を足す（テストの準備）。
	 */
	public void addUser(User user) {
		loginIds.add(user.loginId());
		users.put(user.id(), user);
	}

	/**
	 * 保存したユーザーを読む（テストの確認）。
	 */
	public Optional<User> user(UserId id) {
		return Optional.ofNullable(users.get(id));
	}

	/**
	 * 次のパスワードの試行の保存を、ほかの試行に先を越されたことにする（テストの準備）。
	 */
	public void conflictOnNextPasswordAttempt() {
		conflictOnNextPasswordAttempt = true;
	}

	@Override
	public boolean existsByLoginId(LoginId loginId) {
		return loginIds.contains(loginId);
	}

	@Override
	public Optional<User> findByLoginId(LoginId loginId) {
		return users.values().stream().filter(user -> user.loginId().equals(loginId)).findFirst();
	}

	@Override
	public boolean savePasswordAttempt(User current, User counted) {
		if (conflictOnNextPasswordAttempt) {
			conflictOnNextPasswordAttempt = false;
			return false;
		}
		User stored = users.get(current.id());
		if (stored == null || stored.passwordFailureCount() != current.passwordFailureCount()
				|| !stored.lockedUntil().equals(current.lockedUntil())) {
			return false;
		}
		users.put(counted.id(), counted);
		return true;
	}

	@Override
	public void savePasswordVerified(User verified) {
		if (!users.containsKey(verified.id())) {
			throw new IllegalStateException("ユーザーが無い");
		}
		users.put(verified.id(), verified);
	}

}
