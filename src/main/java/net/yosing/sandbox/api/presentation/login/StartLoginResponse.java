package net.yosing.sandbox.api.presentation.login;

import java.time.Instant;

import net.yosing.sandbox.api.application.login.StartLoginResult;

/**
 * ログインの開始の応答（200 OK）。
 *
 * @param loginToken 仮認証 JWT（BFF が HttpOnly の Cookie に保存し、ログインの確定で渡す）
 * @param expiresAt 仮認証の期限
 */
record StartLoginResponse(String loginToken, Instant expiresAt) {

	static StartLoginResponse from(StartLoginResult result) {
		return new StartLoginResponse(result.loginToken(), result.expiresAt());
	}

	@Override
	public String toString() {
		return "StartLoginResponse[loginToken=****, expiresAt=" + this.expiresAt + "]";
	}

}
