package net.yosing.sandbox.api.domain.model.registration;

import java.util.Objects;
import java.util.UUID;

/**
 * 仮登録の ID（登録 ID）。推測できないランダムな値（UUID）にする。
 *
 * @param value ID の値
 */
public record RegistrationId(UUID value) {

	public RegistrationId {
		Objects.requireNonNull(value, "value");
	}

	/**
	 * 新しい ID を採番する。
	 * @return 新しい ID
	 */
	public static RegistrationId generate() {
		return new RegistrationId(UUID.randomUUID());
	}

	/**
	 * 文字列（パスの値・保存した値）から ID を作る。
	 * @param value UUID の文字列
	 * @return ID
	 * @throws IllegalArgumentException UUID の形式でない場合
	 */
	public static RegistrationId parse(String value) {
		return new RegistrationId(UUID.fromString(value));
	}

	@Override
	public String toString() {
		return this.value.toString();
	}

}
