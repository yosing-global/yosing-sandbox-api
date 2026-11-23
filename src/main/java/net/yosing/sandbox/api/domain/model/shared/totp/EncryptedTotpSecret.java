package net.yosing.sandbox.api.domain.model.shared.totp;

import java.util.Arrays;
import java.util.Objects;

/**
 * 暗号化した TOTP の秘密情報（DB に保存する形）。
 *
 * <p>復号には、暗号化したときと同じユーザー・環境が要る（暗号化コンテキスト）。
 */
public final class EncryptedTotpSecret {

	private final byte[] value;

	/**
	 * @param value 暗号文のバイト列（コピーして持つ）
	 */
	public EncryptedTotpSecret(byte[] value) {
		Objects.requireNonNull(value, "value");
		if (value.length == 0) {
			throw new IllegalArgumentException("暗号文が空");
		}
		this.value = value.clone();
	}

	/**
	 * @return 暗号文のバイト列（コピー）
	 */
	public byte[] bytes() {
		return this.value.clone();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof EncryptedTotpSecret secret && Arrays.equals(this.value, secret.value);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(this.value);
	}

	@Override
	public String toString() {
		return "EncryptedTotpSecret[" + this.value.length + " bytes]";
	}

}
