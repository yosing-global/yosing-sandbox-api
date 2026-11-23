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
	 * @param registration 仮登録
	 * @throws RuntimeException 同じ登録 ID の仮登録が既にある場合（ID はランダムなため、ふつうは起きない）
	 */
	void add(Registration registration);

	/**
	 * 仮登録を取り出す（AP3）。期限が切れていても、DB から消えるまでは返す（期限の確認は呼ぶ側で行う）。
	 * @param id 登録 ID
	 * @return 仮登録。無ければ空
	 */
	Optional<Registration> findById(RegistrationId id);

	/**
	 * OTP の試行を1回数える（AP4）。照合の前に呼ぶ。DB の値に加えるため、同時に試されても数え漏れが無い。
	 *
	 * <p>仮登録があり、試行が上限（{@link Registration#MAX_OTP_FAILURES}）に達していないことを条件にする
	 * （数えられた試行だけが照合に進むため、同時に試されても上限を超えて照合しない）。
	 * @param id 登録 ID
	 * @return 数えた後の仮登録。条件に合わなければ空（仮登録が無い、または上限に達した）
	 */
	Optional<Registration> countOtpAttempt(RegistrationId id);

	/**
	 * 仮登録を削除する（OTP の失敗が上限に達したとき）。無くても失敗しない。
	 * @param id 登録 ID
	 */
	void delete(RegistrationId id);

}
