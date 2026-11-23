package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.application.registration.LoginIdTakenException;
import net.yosing.sandbox.api.application.registration.RegistrationNotFoundException;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.User;

/**
 * 登録確定の保存（AP5）。集約（仮登録・ユーザー・セッション）をまたぐため、1つのトランザクションで書く。
 *
 * <p>ユーザーとログイン ID の作成、仮登録の削除、セッションの作成を、すべて行うか、何もしない。
 * 実装は infrastructure.dynamodb（TransactWriteItems）。
 */
public interface RegistrationCompletionStore {

	/**
	 * 本登録を保存する。
	 * @param registration 確定する仮登録（削除する）
	 * @param user 作るユーザー
	 * @param session 作るセッション
	 * @throws LoginIdTakenException 同じログイン ID が先に本登録されていた場合
	 * @throws RegistrationNotFoundException 仮登録が既に無い場合（同時に確定された、期限で消えた等）
	 */
	void complete(Registration registration, User user, Session session);

}
