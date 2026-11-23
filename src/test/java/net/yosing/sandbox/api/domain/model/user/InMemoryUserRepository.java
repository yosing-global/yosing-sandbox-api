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
		this.loginIds.add(loginId);
	}

	/**
	 * ユーザーとログイン ID を足す（テストの準備）。
	 */
	public void addUser(User user) {
		this.loginIds.add(user.loginId());
		this.users.put(user.id(), user);
	}

	/**
	 * 保存したユーザーを読む（テストの確認）。
	 */
	public Optional<User> user(UserId id) {
		return Optional.ofNullable(this.users.get(id));
	}

	/**
	 * 次のパスワードの試行の保存を、ほかの試行に先を越されたことにする（テストの準備）。
	 */
	public void conflictOnNextPasswordAttempt() {
		this.conflictOnNextPasswordAttempt = true;
	}

	@Override
	public boolean existsByLoginId(LoginId loginId) {
		return this.loginIds.contains(loginId);
	}

	@Override
	public Optional<User> findByLoginId(LoginId loginId) {
		return this.users.values().stream().filter((user) -> user.loginId().equals(loginId)).findFirst();
	}

	/**
	 * ユーザーを置き換える（フェイクの保存先から使う）。
	 */
	public void replace(User user) {
		if (!this.users.containsKey(user.id())) {
			throw new IllegalStateException("ユーザーが無い");
		}
		this.users.put(user.id(), user);
	}

	@Override
	public Optional<User> findById(UserId id) {
		return Optional.ofNullable(this.users.get(id));
	}

	@Override
	public Optional<User> countOtpAttempt(UserId id, String loginTokenId) {
		User stored = this.users.get(id);
		if (stored == null || !stored.hasLoginToken(loginTokenId) || stored.otpAttemptsExhausted()) {
			return Optional.empty();
		}
		User counted = User.restore(stored.id(), stored.loginId(), stored.passwordHash(), stored.totpSecret(),
				stored.lastUsedTotpStep(), stored.passwordFailureCount(), stored.otpFailureCount() + 1,
				stored.lockedUntil(), stored.loginTokenId(), stored.createdAt());
		this.users.put(id, counted);
		return Optional.of(counted);
	}

	@Override
	public boolean saveOtpLock(User current, User locked) {
		User stored = this.users.get(current.id());
		if (stored == null || !stored.loginTokenId().equals(current.loginTokenId())) {
			return false;
		}
		this.users.put(stored.id(),
				User.restore(stored.id(), stored.loginId(), stored.passwordHash(), stored.totpSecret(),
						stored.lastUsedTotpStep(), stored.passwordFailureCount(), stored.otpFailureCount(),
						locked.lockedUntil(), Optional.empty(), stored.createdAt()));
		return true;
	}

	@Override
	public boolean savePasswordAttempt(User current, User counted) {
		if (this.conflictOnNextPasswordAttempt) {
			this.conflictOnNextPasswordAttempt = false;
			return false;
		}
		User stored = this.users.get(current.id());
		if (stored == null || stored.passwordFailureCount() != current.passwordFailureCount()
				|| stored.otpFailureCount() != current.otpFailureCount()
				|| !stored.lockedUntil().equals(current.lockedUntil())) {
			return false;
		}
		this.users.put(counted.id(), counted);
		return true;
	}

	@Override
	public void savePasswordVerified(User verified) {
		if (!this.users.containsKey(verified.id())) {
			throw new IllegalStateException("ユーザーが無い");
		}
		this.users.put(verified.id(), verified);
	}

}
