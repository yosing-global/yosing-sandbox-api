package net.yosing.sandbox.api.application.login;

/**
 * ログイン ID かパスワードが違う（ロック中を含む）。API では 401 {@code INVALID_CREDENTIALS} にする。
 *
 * <p>ログイン ID が無い・パスワードが違う・ロック中のどれかは、応答で見分けられないようにする。
 */
public class InvalidCredentialsException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidCredentialsException() {
		super("ログイン ID かパスワードが違う");
	}

}
