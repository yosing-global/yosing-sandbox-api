package net.yosing.sandbox.api.application.login;

/**
 * 仮認証 JWT が無効・期限切れ・使用済み（置き換わった仮認証を含む）。API では 401 {@code LOGIN_TOKEN_INVALID} にする。
 */
public class LoginTokenInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoginTokenInvalidException() {
		super("仮認証 JWT が無効");
	}

}
