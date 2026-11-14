package net.yosing.sandbox.api.application.error;

/**
 * OTP コードが違う、または使用済み。API では 401 {@code OTP_INVALID}（残りの回数を付ける）にする。
 *
 * <p>登録の確定とログインの確定で使う。
 */
public class OtpInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final int remainingAttempts;

	/**
	 * @param remainingAttempts 残りの回数
	 */
	public OtpInvalidException(int remainingAttempts) {
		super("OTP コードが違う");
		this.remainingAttempts = remainingAttempts;
	}

	public int remainingAttempts() {
		return remainingAttempts;
	}

}
