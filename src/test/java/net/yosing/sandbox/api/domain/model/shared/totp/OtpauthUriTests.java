package net.yosing.sandbox.api.domain.model.shared.totp;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 認証アプリの設定 URI の形と、秘密情報の Base32 の符号化を確かめる。
 */
class OtpauthUriTests {

	// RFC 6238 の試験用の秘密情報（Base32 にすると GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ）
	private static final TotpSecret SECRET = new TotpSecret("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

	/**
	 * 発行者・アカウント名・秘密情報（Base32）・TOTP の決まりを含む URI を作る。
	 */
	@Test
	void createsUri() {
		OtpauthUri uri = OtpauthUri.create("dummy-sandbox", "dummy_alice", SECRET);

		assertThat(uri.value()).isEqualTo("otpauth://totp/dummy-sandbox:dummy_alice"
				+ "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=dummy-sandbox&algorithm=SHA1&digits=6&period=30");
	}

	/**
	 * 発行者の空白等は、URI の形に符号化する（空白は %20）。
	 */
	@Test
	void encodesIssuer() {
		assertThat(OtpauthUri.create("dummy sandbox", "dummy_alice", SECRET).value())
			.startsWith("otpauth://totp/dummy%20sandbox:dummy_alice?")
			.contains("&issuer=dummy%20sandbox&");
	}

	/**
	 * 5ビットで割り切れない長さも、末尾の「=」なしで符号化する（RFC 4648 の試験用の値）。
	 */
	@Test
	void encodesBase32WithoutPadding() {
		assertThat(Base32.encode("f".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MY");
		assertThat(Base32.encode("foobar".getBytes(StandardCharsets.US_ASCII))).isEqualTo("MZXW6YTBOI");
	}

	/**
	 * toString（ログ等）に秘密情報を出さない。
	 */
	@Test
	void doesNotShowValueInToString() {
		assertThat(OtpauthUri.create("dummy-sandbox", "dummy_alice", SECRET).toString()).doesNotContain("GEZDGNBV");
	}

}
