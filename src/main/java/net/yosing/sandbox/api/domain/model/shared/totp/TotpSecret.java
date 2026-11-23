package net.yosing.sandbox.api.domain.model.shared.totp;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/**
 * TOTP の秘密情報（認証アプリと共有する鍵）
 *
 * <p>ログ等に出ないよう、toString では中身を出さない。保存するときは暗号化する（{@link EncryptedTotpSecret}）。
 */
public final class TotpSecret {

	// SecureRandom はスレッドセーフ（全部の要求で共有してよい）
	private static final SecureRandom RANDOM = new SecureRandom();

	private final byte[] value;

	/**
	 * @param value 秘密情報のバイト列（コピーして持つ）
	 */
	public TotpSecret(byte[] value) {
		Objects.requireNonNull(value, "value");
		if (value.length == 0) {
			throw new IllegalArgumentException("秘密情報が空");
		}
		this.value = value.clone();
	}

	/**
	 * 新しい秘密情報をランダムに作る（{@link TotpParameters#SECRET_BYTES} バイト）。
	 * @return 秘密情報
	 */
	public static TotpSecret generate() {
		byte[] value = new byte[TotpParameters.SECRET_BYTES];
		RANDOM.nextBytes(value);
		return new TotpSecret(value);
	}

	/**
	 * @return 秘密情報のバイト列（コピー）
	 */
	public byte[] bytes() {
		return this.value.clone();
	}

	@Override
	public boolean equals(Object other) {
		// 比べる時間で中身が推測されないよう、一定時間で比べる
		return other instanceof TotpSecret secret && MessageDigest.isEqual(this.value, secret.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.value);
	}

	@Override
	public String toString() {
		return "TotpSecret[****]";
	}

}
