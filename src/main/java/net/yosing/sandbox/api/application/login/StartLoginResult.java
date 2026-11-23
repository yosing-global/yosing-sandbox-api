package net.yosing.sandbox.api.application.login;

import java.time.Instant;
import java.util.Objects;

/**
 * ログインの開始の結果。
 *
 * <p>仮認証 JWT はログ等に出ないよう、toString では中身を出さない。
 *
 * @param loginToken 仮認証 JWT
 * @param expiresAt 仮認証の期限
 */
public record StartLoginResult(String loginToken, Instant expiresAt) {

	public StartLoginResult {
		Objects.requireNonNull(loginToken, "loginToken");
		Objects.requireNonNull(expiresAt, "expiresAt");
	}

	@Override
	public String toString() {
		return "StartLoginResult[loginToken=****, expiresAt=" + this.expiresAt + "]";
	}

}
