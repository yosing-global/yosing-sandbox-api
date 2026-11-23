package net.yosing.sandbox.api.domain.model.session;

import java.util.Optional;

/**
 * セッションの取り出し・延長・削除。実装は infrastructure.dynamodb。
 *
 * <p>セッションの作成は、本登録・ログインの確定のトランザクション（application.port の *CompletionStore）で行う。
 */
public interface SessionRepository {

	/**
	 * セッションを取り出す（AP11）。期限が切れていても、DB から消えるまでは返す（期限の確認は呼ぶ側で行う）。
	 * @param idHash セッション ID のハッシュ
	 * @return セッション。無ければ空
	 */
	Optional<Session> findByIdHash(SessionIdHash idHash);

	/**
	 * 延ばしたセッション（最終操作の日時・期限）を保存する（AP14）。セッションがあることを条件にする
	 * （同時にログアウトされたセッションを作り直さない）。
	 * @param touched {@link Session#touch} の結果
	 * @return 保存できたら true。セッションが無ければ false
	 */
	boolean saveAccess(Session touched);

	/**
	 * セッションを削除する（AP12。ログアウト）。無くても失敗しない。
	 * @param idHash セッション ID のハッシュ
	 */
	void delete(SessionIdHash idHash);

}
