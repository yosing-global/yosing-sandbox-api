package net.yosing.sandbox.api.domain.model.registration;

/**
 * 仮登録の保存と取り出し。実装は infrastructure.dynamodb。
 */
public interface RegistrationRepository {

	/**
	 * 新しい仮登録を保存する（AP2）。期限が過ぎたら DB からも消える（TTL）。
	 *
	 * @param registration 仮登録
	 * @throws RuntimeException 同じ登録 ID の仮登録が既にある場合（ID はランダムなため、ふつうは起きない）
	 */
	void add(Registration registration);

}
