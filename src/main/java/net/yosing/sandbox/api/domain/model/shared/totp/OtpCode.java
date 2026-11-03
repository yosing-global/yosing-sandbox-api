package net.yosing.sandbox.api.domain.model.shared.totp;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 利用者が入力した OTP コード（数字6桁）。
 *
 * <p>ログ等に出ないよう、toString では中身を出さない。
 *
 * @param value コード
 */
public record OtpCode(String value) {

	/**
	 * 形式。入力の検証（presentation）でも使う。
	 */
	public static final String PATTERN = "[0-9]{6}";

	private static final Pattern FORMAT = Pattern.compile(PATTERN);

	public OtpCode {
		Objects.requireNonNull(value, "value");
		if (!FORMAT.matcher(value).matches()) {
			throw new IllegalArgumentException("OTP コードの形式が違う");
		}
	}

	@Override
	public String toString() {
		return "OtpCode[****]";
	}

}
