package net.yosing.sandbox.api.domain.model.user;

/**
 * ユーザー（とログイン ID の項目）の保存と取り出し。実装は infrastructure.dynamodb。
 */
public interface UserRepository {

	/**
	 * ログイン ID が使われているか（本登録済みか）を確かめる（AP1）。仮登録中のログイン ID は含めない。
	 *
	 * @param loginId ログイン ID
	 * @return 使われていれば true
	 */
	boolean existsByLoginId(LoginId loginId);

}
