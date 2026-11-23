package net.yosing.sandbox.api.infrastructure.password;

import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * bcrypt（強さ12、先頭に方式）でハッシュにすることを確かめる。
 */
class BcryptPasswordHasherTests {

	private static final Password PASSWORD = new Password("dummy correct horse battery");

	private final BcryptPasswordHasher hasher = new BcryptPasswordHasher();

	/**
	 * ハッシュの先頭は {bcrypt}$2a$12$ で、元のパスワードと照合できる。
	 */
	@Test
	void hashesWithBcryptStrength12() {
		PasswordHash hash = this.hasher.hash(PASSWORD);

		assertThat(hash.value()).startsWith("{bcrypt}$2a$12$");
		assertThat(new BCryptPasswordEncoder().matches(PASSWORD.value(), hash.value().substring("{bcrypt}".length())))
			.isTrue();
	}

	/**
	 * 作ったハッシュは、同じパスワードとだけ合う。
	 */
	@Test
	void matchesOnlySamePassword() {
		PasswordHash hash = this.hasher.hash(PASSWORD);

		assertThat(this.hasher.matches(PASSWORD, hash)).isTrue();
		assertThat(this.hasher.matches(new Password("dummy wrong horse battery"), hash)).isFalse();
	}

	/**
	 * 同じパスワードでも、毎回違うハッシュになる（ソルト）。
	 */
	@Test
	void producesDifferentHashEachTime() {
		assertThat(this.hasher.hash(PASSWORD)).isNotEqualTo(this.hasher.hash(PASSWORD));
	}

}
