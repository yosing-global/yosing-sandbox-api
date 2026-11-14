package net.yosing.sandbox.api.domain.model.session;

import java.util.Objects;

/**
 * セッション ID の SHA-256 のハッシュ（16進数の小文字、64文字）。DB のキーにする。
 *
 * @param value ハッシュ
 */
public record SessionIdHash(String value) {

	public SessionIdHash {
		Objects.requireNonNull(value, "value");
		if (!value.matches("[0-9a-f]{64}")) {
			throw new IllegalArgumentException("セッション ID のハッシュの形式が違う");
		}
	}

	@Override
	public String toString() {
		return value;
	}

}
