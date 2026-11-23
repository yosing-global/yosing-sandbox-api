package net.yosing.sandbox.api.domain.model.user;

import java.util.Objects;
import java.util.UUID;

/**
 * ユーザーの ID。推測できないランダムな値（UUID）にする。
 *
 * <p>TOTP の秘密情報の暗号化コンテキストにも使う。
 *
 * @param value ID の値
 */
public record UserId(UUID value) {

	public UserId {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * 新しい ID を採番する。
	 * @return 新しい ID
	 */
	public static UserId generate() {
		return new UserId(UUID.randomUUID());
	}

	/**
	 * 文字列（保存した値・JWT の sub 等）から ID を作る。
	 * @param value UUID の文字列
	 * @return ID
	 * @throws IllegalArgumentException UUID の形式でない場合
	 */
	public static UserId parse(String value) {
		return new UserId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return this.value.toString();
	}

}
