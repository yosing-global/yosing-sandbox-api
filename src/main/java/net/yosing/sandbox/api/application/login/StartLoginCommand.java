package net.yosing.sandbox.api.application.login;

import java.util.Objects;

import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;

/**
 * ログインの開始の入力。
 *
 * @param loginId ログイン ID（小文字にそろえたもの）
 * @param password パスワード
 */
public record StartLoginCommand(LoginId loginId, Password password) {

	public StartLoginCommand {
		Objects.requireNonNull(loginId, "loginId");
		Objects.requireNonNull(password, "password");
	}

}
