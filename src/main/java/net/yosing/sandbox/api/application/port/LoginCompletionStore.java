package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.User;

/**
 * ログインの確定の保存（AP9）。集約（ユーザー・セッション）をまたぐため、1つのトランザクションで書く。
 *
 * <p>ユーザーの更新（最後に使ったステップ、OTP の失敗回数を0、仮認証の終了）とセッションの作成を、すべて行うか、何もしない。
 * ユーザーの更新は、仮認証が置き換わっていないことと、一致したステップが最後に使ったステップより後であることを条件にする
 * （同じコードを同時に使われても、1つしか通らない）。実装は infrastructure.dynamodb（TransactWriteItems）。
 */
public interface LoginCompletionStore {

	/**
	 * ログインの確定を保存する。
	 *
	 * @param current OTP の試行を数えた後のユーザー（条件に使う jti を持つ）
	 * @param verified {@link User#otpVerified} の結果
	 * @param session 作るセッション
	 * @return 保存できれば true。ユーザーの条件に合わなければ false（同じコードが先に使われた、仮認証が置き換わった等）
	 */
	boolean complete(User current, User verified, Session session);

}
