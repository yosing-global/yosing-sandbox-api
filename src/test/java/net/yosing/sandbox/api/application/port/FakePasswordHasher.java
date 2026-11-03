package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;

/**
 * テスト用のパスワードのハッシュ（フェイク）。bcrypt は遅いため、決まった形の値にする。
 */
public class FakePasswordHasher implements PasswordHasher {

	@Override
	public PasswordHash hash(Password password) {
		return new PasswordHash("{dummy}" + password.value());
	}

}
