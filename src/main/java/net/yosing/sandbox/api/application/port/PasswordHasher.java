package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;

/**
 * パスワードのハッシュ化。実装は infrastructure.password（bcrypt）。
 */
public interface PasswordHasher {

	/**
	 * パスワードをハッシュにする。同じパスワードでも、毎回違うハッシュになる（ソルト）。
	 *
	 * @param password パスワード
	 * @return ハッシュ（先頭に方式が付く）
	 */
	PasswordHash hash(Password password);

}
