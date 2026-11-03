package net.yosing.sandbox.api.application.registration;

/**
 * ログイン ID が使われている（本登録済み）。API では 409 {@code LOGIN_ID_TAKEN} にする。
 */
public class LoginIdTakenException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public LoginIdTakenException() {
		super("ログイン ID が使われている");
	}

}
