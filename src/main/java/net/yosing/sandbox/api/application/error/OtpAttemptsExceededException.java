package net.yosing.sandbox.api.application.error;

/**
 * OTP の失敗が上限に達した（仮登録・仮認証は無効にした）。API では 429 {@code OTP_ATTEMPTS_EXCEEDED} にする。
 *
 * <p>登録の確定とログインの確定で使う。
 */
public class OtpAttemptsExceededException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public OtpAttemptsExceededException() {
		super("OTP の失敗が上限に達した");
	}

}
