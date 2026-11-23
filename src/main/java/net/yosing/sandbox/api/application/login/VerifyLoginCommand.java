package net.yosing.sandbox.api.application.login;

import java.util.Objects;

import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;

/**
 * ログイン確定の入力
 *
 * <p>仮認証 JWT はログ等に出ないよう、toString では中身を出さない。
 *
 * @param loginToken 仮認証 JWT
 * @param code OTP コード
 */
public record VerifyLoginCommand(String loginToken, OtpCode code) {

	public VerifyLoginCommand {
		Objects.requireNonNull(loginToken, "loginToken");
		Objects.requireNonNull(code, "code");
	}

	@Override
	public String toString() {
		return "VerifyLoginCommand[loginToken=****, code=" + this.code + "]";
	}

}
