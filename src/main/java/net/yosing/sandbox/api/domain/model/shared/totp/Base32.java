package net.yosing.sandbox.api.domain.model.shared.totp;

/**
 * Base32（RFC 4648）の符号化。認証アプリに秘密情報を渡す形式（otpauth URI の secret）。
 *
 * <p>認証アプリの慣習に合わせ、末尾の「=」は付けない。
 */
final class Base32 {

	private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

	private static final int BITS_PER_CHAR = 5;

	private static final int MASK = 0x1F;

	private Base32() {
	}

	static String encode(byte[] bytes) {
		StringBuilder encoded = new StringBuilder((bytes.length * 8 + BITS_PER_CHAR - 1) / BITS_PER_CHAR);
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes) {
			buffer = (buffer << 8) | (b & 0xFF);
			bits += 8;
			while (bits >= BITS_PER_CHAR) {
				bits -= BITS_PER_CHAR;
				encoded.append(ALPHABET[(buffer >> bits) & MASK]);
			}
		}
		if (bits > 0) {
			encoded.append(ALPHABET[(buffer << (BITS_PER_CHAR - bits)) & MASK]);
		}
		return encoded.toString();
	}

}
