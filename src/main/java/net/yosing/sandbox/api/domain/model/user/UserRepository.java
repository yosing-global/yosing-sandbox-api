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
	 * ID でユーザーを取り出す（AP8）。
	 *
	 * @param id ユーザーの ID
	 * @return ユーザー。無ければ空
	 */
	Optional<User> findById(UserId id);

	/**
	 * OTP の試行を1回数える（AP10）。照合の前に呼ぶ。DB の値に加えるため、同時に試されても数え漏れが無い。
	 *
	 * <p>今の仮認証の jti が {@code loginTokenId} で、試行が上限（{@link User#MAX_OTP_FAILURES}）に達していないことを条件にする。
	 *
	 * @param id ユーザーの ID
	 * @param loginTokenId 仮認証 JWT の jti
	 * @return 数えた後のユーザー。条件に合わなければ空（仮認証が置き換わった・終わった、または上限に達した）
	 */
	Optional<User> countOtpAttempt(UserId id, String loginTokenId);

	/**
	 * OTP の失敗が上限に達したときのロック（ロックの期限、仮認証の終了）を保存する。
	 *
	 * <p>今の jti が {@code current} の jti のときだけ書く（その間に始まった新しい仮認証は消さない。
	 * その仮認証でも、失敗回数が上限のため照合には進めない）。
	 *
	 * @param current OTP の試行を数えた後のユーザー（条件に使う jti を持つ）
	 * @param locked {@link User#lockForOtpFailures} の結果
	 * @return 保存できたら true。仮認証が置き換わっていた、または既に終わっていたら false
	 */
	boolean saveOtpLock(User current, User locked);

	/**
	 * パスワードの試行を数えた結果（パスワード・OTP の失敗回数、ロックの期限）を保存する（AP7）。
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
	 * パスワードが合ったときの状態（パスワードの失敗回数を0、ロックなし、今の仮認証の jti）を保存する（AP13）。
	 * OTP の失敗回数は変えない。
	 *
	 * @param verified {@link User#passwordVerified} の結果
	 */
	void savePasswordVerified(User verified);

}
