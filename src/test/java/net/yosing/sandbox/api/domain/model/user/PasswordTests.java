package net.yosing.sandbox.api.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * パスワードの規則（12〜64文字の表示できる ASCII）と、中身を漏らさないことを確かめる。
 */
class PasswordTests {

	/**
	 * 12文字と64文字は受け付ける（長さの境界）。記号と空白も使える。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "dummy 123!@#", "dummy-0123456789012345678901234567890123456789012345678901234567" })
	void acceptsBoundaryLengths(String value) {
		assertThat(new Password(value).value()).isEqualTo(value);
	}

	/**
	 * 短すぎる・長すぎる・ASCII 以外の文字や制御文字を含むものは受け付けない。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "dummy-12345", "dummy-01234567890123456789012345678901234567890123456789012345678",
			"ダミーのパスワードダミーのパスワード", "dummy-12345\t" })
	void rejectsInvalidFormat(String value) {
		assertThatIllegalArgumentException().isThrownBy(() -> new Password(value));
	}

	/**
	 * toString（ログ等）に中身を出さない。例外のメッセージにも出さない。
	 */
	@Test
	void doesNotShowValue() {
		assertThat(new Password("dummy correct horse battery").toString()).doesNotContain("correct");
		assertThatIllegalArgumentException().isThrownBy(() -> new Password("dummy"))
			.withMessageNotContaining("dummy");
	}

	/**
	 * パスワードのハッシュも、toString に中身を出さない。
	 */
	@Test
	void hashDoesNotShowValue() {
		assertThat(new PasswordHash("{bcrypt}$2a$12$dummy").toString()).doesNotContain("bcrypt", "dummy");
	}

}
