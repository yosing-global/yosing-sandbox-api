package net.yosing.sandbox.api.domain.model.user;

import java.util.Objects;

/**
 * パスワードのハッシュ（DB に保存する形）。先頭に方式が付く（例：{@code {bcrypt}$2a$12$...}）。
 *
 * <p>ハッシュでも総当たりの手がかりになるため、toString では中身を出さない。
 *
 * @param value ハッシュ
 */
public record PasswordHash(String value) {

	public PasswordHash {
		Objects.requireNonNull(value, "value");
		if (value.isBlank()) {
			throw new IllegalArgumentException("パスワードのハッシュが空");
		}
	}

	@Override
	public String toString() {
		return "PasswordHash[****]";
	}

}
