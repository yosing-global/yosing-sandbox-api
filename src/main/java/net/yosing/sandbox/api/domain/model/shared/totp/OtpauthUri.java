package net.yosing.sandbox.api.domain.model.shared.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 認証アプリの設定 URI（{@code otpauth://totp/...}）。BFF が QR コードにして表示する。
 *
 * <p>秘密情報を含むため、toString では中身を出さない。
 *
 * @param value URI
 */
public record OtpauthUri(String value) {

	public OtpauthUri {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * 設定 URI を作る。認証アプリには「発行者:アカウント名」で表示される。
	 *
	 * @param issuer 発行者（サービスの名前）
	 * @param accountName アカウント名（ログイン ID）
	 * @param secret 秘密情報
	 * @return 設定 URI
	 */
	public static OtpauthUri create(String issuer, String accountName, TotpSecret secret) {
		String encodedIssuer = encode(issuer);
		return new OtpauthUri("otpauth://totp/" + encodedIssuer + ":" + encode(accountName)
				+ "?secret=" + Base32.encode(secret.bytes())
				+ "&issuer=" + encodedIssuer
				+ "&algorithm=" + TotpParameters.ALGORITHM
				+ "&digits=" + TotpParameters.DIGITS
				+ "&period=" + TotpParameters.PERIOD.toSeconds());
	}

	// URLEncoder は空白を「+」にするため、URI の形（%20）に直す
	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
	}

	@Override
	public String toString() {
		return "OtpauthUri[****]";
	}

}
