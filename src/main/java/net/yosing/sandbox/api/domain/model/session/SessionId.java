package net.yosing.sandbox.api.domain.model.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * セッション ID（クライアントに渡す元の値）。BFF が Cookie に入れ、API には {@code Authorization: Bearer} で届く。
 *
 * <p>256 ビットの乱数を Base64URL（末尾の「=」なし、43文字）にしたもの。DB には元の値を保存せず、
 * SHA-256 のハッシュ（{@link SessionIdHash}）をキーにする（DB の中身が漏れても、そのままセッションを乗っ取られないようにする）。
 * ログ等に出ないよう、toString では中身を出さない。
 */
public final class SessionId {

	private static final int RANDOM_BYTES = 32;

	// 発行する形（Base64URL、末尾の「=」なし、43文字）
	private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

	// SecureRandom はスレッドセーフ（全部の要求で共有してよい）
	private static final SecureRandom RANDOM = new SecureRandom();

	private final String value;

	/**
	 * @param value セッション ID の元の値
	 */
	public SessionId(String value) {
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) {
			throw new IllegalArgumentException("セッション ID が空");
		}
		this.value = value;
	}

	/**
	 * 新しいセッション ID をランダムに作る。
	 *
	 * @return セッション ID
	 */
	public static SessionId generate() {
		byte[] bytes = new byte[RANDOM_BYTES];
		RANDOM.nextBytes(bytes);
		return new SessionId(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
	}

	/**
	 * クライアントから受け取った値をセッション ID にする。発行する形（43文字の Base64URL）でなければ空
	 * （DB を読まずに、無効なセッションとして扱う）。
	 *
	 * @param value クライアントから受け取った値
	 * @return セッション ID。形が違えば空
	 */
	public static Optional<SessionId> fromClient(String value) {
		if (value == null || !FORMAT.matcher(value).matches()) {
			return Optional.empty();
		}
		return Optional.of(new SessionId(value));
	}

	/**
	 * @return SHA-256 のハッシュ（DB のキーにする）
	 */
	public SessionIdHash hash() {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
			return new SessionIdHash(HexFormat.of().formatHex(digest));
		} catch (NoSuchAlgorithmException ex) {
			// SHA-256 は Java の標準で必ず使える
			throw new IllegalStateException(ex);
		}
	}

	/**
	 * @return セッション ID の元の値（クライアントに返すときだけ使う）
	 */
	public String value() {
		return value;
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof SessionId sessionId && MessageDigest.isEqual(
				value.getBytes(StandardCharsets.US_ASCII), sessionId.value.getBytes(StandardCharsets.US_ASCII));
	}

	@Override
	public int hashCode() {
		return value.hashCode();
	}

	@Override
	public String toString() {
		return "SessionId[****]";
	}

}
