package net.yosing.sandbox.api.infrastructure.password;

import java.util.Map;

import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * bcrypt（強さ12）でパスワードをハッシュにする。
 *
 * <p>Spring Security の DelegatingPasswordEncoder を使い、ハッシュの先頭に方式（{@code {bcrypt}}）を付ける
 * （後で方式や強さを変えても、保存済みのハッシュを照合できる）。
 */
@Component
final class BcryptPasswordHasher implements PasswordHasher {

	private static final String BCRYPT = "bcrypt";

	private static final int STRENGTH = 12;

	// スレッドセーフ（全部の要求で共有してよい）
	private final PasswordEncoder encoder = new DelegatingPasswordEncoder(BCRYPT,
			Map.of(BCRYPT, new BCryptPasswordEncoder(STRENGTH)));

	@Override
	public PasswordHash hash(Password password) {
		// encode は、渡したパスワードが null のときだけ null を返す（Spring Security 7 で戻り値が @Nullable になった）。
		// ここでは null を渡さないが、null のまま進めないよう確かめる
		String hash = encoder.encode(password.value());
		if (hash == null) {
			throw new IllegalStateException("パスワードのハッシュを作れなかった");
		}
		return new PasswordHash(hash);
	}

	@Override
	public boolean matches(Password password, PasswordHash hash) {
		return encoder.matches(password.value(), hash.value());
	}

}
