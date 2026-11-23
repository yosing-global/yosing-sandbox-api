package net.yosing.sandbox.api.presentation.session;

import java.time.Instant;

import net.yosing.sandbox.api.application.session.CurrentUserResult;

/**
 * ログイン中のユーザーの情報の応答（200 OK）
 *
 * @param loginId ログイン ID
 * @param createdAt 登録した日時
 * @param sessionExpiresAt 延ばした後のセッションの期限（BFF がセッションの Cookie の期限をそろえる）
 */
record MeResponse(String loginId, Instant createdAt, Instant sessionExpiresAt) {

	static MeResponse from(CurrentUserResult result) {
		return new MeResponse(result.loginId().value(), result.createdAt(), result.sessionExpiresAt());
	}

}
