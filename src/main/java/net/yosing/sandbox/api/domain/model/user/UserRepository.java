package net.yosing.sandbox.api.domain.model.user;

import java.util.Optional;

/**
 * ユーザー（とログイン ID の項目）の保存と取り出し。実装は infrastructure.dynamodb。
 *
 * <p>本登録（ユーザーの作成）は、application.port の RegistrationCompletionStore で行う。
 */
public interface UserRepository {

	/**
	 * ログイン ID が使われているか（本登録済みか）を確かめる（AP1）。仮登録中のログイン ID は含めない。
	 *
	 * @param loginId ログイン ID
	 * @return 使われていれば true
	 */
	boolean existsByLoginId(LoginId loginId);

	/**
	 * ログイン ID でユーザーを取り出す（AP6。ログイン ID の項目 → ユーザーの項目）。
	 *
	 * @param loginId ログイン ID
	 * @return ユーザー。無ければ空
	 */
	Optional<User> findByLoginId(LoginId loginId);

	/**
	 * パスワードの試行を数えた結果（失敗回数・ロックの期限）を保存する（AP7）。
	 *
	 * <p>読んだときから失敗回数・ロックの期限が変わっていないことを条件に書く。同時に試されたときは、
	 * 先に書いた1つだけが通る（上限を超えて照合しない）。
	 *
	 * @param current 読んだときのユーザー
	 * @param counted {@link User#countPasswordAttempt} の結果
	 * @return 保存できれば true。ほかの試行が先に書いていたら false
	 */
	boolean savePasswordAttempt(User current, User counted);

	/**
	 * パスワードが合ったときの状態（失敗回数を0、ロックなし、今の仮認証の jti）を保存する（AP13）。
	 *
	 * @param verified {@link User#passwordVerified} の結果
	 */
	void savePasswordVerified(User verified);

}
