package net.yosing.sandbox.api.presentation.session;

import java.time.Instant;

import net.yosing.sandbox.api.application.session.CurrentUserResult;

/**
 * ログイン中のユーザーの情報の応答（200 OK）。
 *
 * @param loginId ログイン ID
 * @param createdAt 登録した日時
 */
record MeResponse(String loginId, Instant createdAt) {

	static MeResponse from(CurrentUserResult result) {
		return new MeResponse(result.loginId().value(), result.createdAt());
	}

}
