package net.yosing.sandbox.api.domain.model.registration;

import java.util.Optional;

/**
 * 仮登録の保存と取り出し。実装は infrastructure.dynamodb。
 *
 * <p>本登録（仮登録の削除とユーザーの作成を1つのトランザクションで行う）は、application.port の RegistrationCompletionStore で行う。
 */
public interface RegistrationRepository {

	/**
	 * 新しい仮登録を保存する（AP2）。期限が過ぎたら DB からも消える（TTL）。
	 *
	 * @param registration 仮登録
	 * @throws RuntimeException 同じ登録 ID の仮登録が既にある場合（ID はランダムなため、ふつうは起きない）
	 */
	void add(Registration registration);

	/**
	 * 仮登録を取り出す（AP3）。期限が切れていても、DB から消えるまでは返す（期限の確認は呼ぶ側で行う）。
	 *
	 * @param id 登録 ID
	 * @return 仮登録。無ければ空
	 */
	Optional<Registration> findById(RegistrationId id);

	/**
	 * OTP の失敗回数を1増やす（AP4）。同時に失敗しても数え漏れが無いよう、DB の値に加える。
	 *
	 * @param id 登録 ID
	 * @return 増やした後の仮登録。仮登録が無ければ空
	 */
	Optional<Registration> recordOtpFailure(RegistrationId id);

	/**
	 * 仮登録を削除する（OTP の失敗が上限に達したとき）。無くても失敗しない。
	 *
	 * @param id 登録 ID
	 */
	void delete(RegistrationId id);

}
