package net.yosing.sandbox.api.domain.model.user;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * ログイン ID の規則（3〜32文字の英小文字・数字・_・-、大文字は小文字にそろえる）を確かめる。
 */
class LoginIdTests {

	/**
	 * 大文字は小文字にそろえる。
	 */
	@Test
	void normalizesToLowerCase() {
		assertThat(LoginId.of("Dummy_01-X").value()).isEqualTo("dummy_01-x");
	}

	/**
	 * 3文字と32文字は受け付ける（長さの境界。3文字は dummy を入れられないため、そのまま）。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "abc", "dummy_abcdefghijklmnopqrstuvwxyz" })
	void acceptsBoundaryLengths(String input) {
		assertThat(LoginId.of(input).value()).isEqualTo(input);
	}

	/**
	 * 短すぎる・長すぎる・使えない文字を含むものは受け付けない。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "ab", "dummy_abcdefghijklmnopqrstuvwxyz0", "dummy!", "dummy alice", "ダミー", "" })
	void rejectsInvalidFormat(String input) {
		assertThatIllegalArgumentException().isThrownBy(() -> LoginId.of(input));
	}

	/**
	 * 小文字にすると ASCII になる ASCII 以外の文字（ケルビン記号 U+212A。小文字にすると k）も受け付けない。
	 */
	@Test
	void rejectsNonAsciiThatLowersToAscii() {
		assertThatIllegalArgumentException().isThrownBy(() -> LoginId.of("dummy_\u212A"));
	}

	/**
	 * 小文字にそろえていない値では、直接作れない。
	 */
	@Test
	void rejectsUpperCaseInConstructor() {
		assertThatIllegalArgumentException().isThrownBy(() -> new LoginId("Dummy"));
	}

}
