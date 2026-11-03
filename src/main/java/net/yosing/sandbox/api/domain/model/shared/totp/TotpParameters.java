package net.yosing.sandbox.api.domain.model.shared.totp;

import java.time.Duration;

/**
 * TOTP の決まり（RFC 6238）。認証アプリに渡す設定（{@link OtpauthUri}）と、コードの照合で同じ値を使う。
 */
public final class TotpParameters {

	/**
	 * HMAC のアルゴリズム（認証アプリの多くが対応している SHA1）。
	 */
	public static final String ALGORITHM = "SHA1";

	/**
	 * コードの桁数。
	 */
	public static final int DIGITS = 6;

	/**
	 * 1ステップの長さ。
	 */
	public static final Duration PERIOD = Duration.ofSeconds(30);

	/**
	 * 秘密情報の長さ（160 ビット。RFC 4226 の推奨）。
	 */
	public static final int SECRET_BYTES = 20;

	private TotpParameters() {
	}

}
