package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;

/**
 * テスト用のパスワードのハッシュ（フェイク）。bcrypt は遅いため、決まった形の値にする。
 *
 * <p>照合した回数を数える（照合しないで失敗するときも、時間をかけるために照合していることを確かめる）。
 */
public class FakePasswordHasher implements PasswordHasher {

	private int matchCount;

	@Override
	public PasswordHash hash(Password password) {
		return new PasswordHash("{dummy}" + password.value());
	}

	@Override
	public boolean matches(Password password, PasswordHash hash) {
		this.matchCount++;
		return hash.value().equals("{dummy}" + password.value());
	}

	/**
	 * @return 照合した回数
	 */
	public int matchCount() {
		return this.matchCount;
	}

}
