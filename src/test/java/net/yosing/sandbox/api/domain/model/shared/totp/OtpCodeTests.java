package net.yosing.sandbox.api.domain.model.shared.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * OTP コードの形式（数字6桁）と、TOTP のステップの計算を確かめる。
 */
class OtpCodeTests {

	/**
	 * 先頭が0のコードも使える。
	 */
	@Test
	void acceptsLeadingZero() {
		assertThat(new OtpCode("012345").value()).isEqualTo("012345");
	}

	/**
	 * 数字6桁でないものは受け付けない。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "12345", "1234567", "12345a", "dummy1", "" })
	void rejectsInvalidFormat(String value) {
		assertThatIllegalArgumentException().isThrownBy(() -> new OtpCode(value));
	}

	/**
	 * toString（ログ等）に中身を出さない。
	 */
	@Test
	void doesNotShowValueInToString() {
		assertThat(new OtpCode("123456").toString()).doesNotContain("123456");
	}

	/**
	 * ステップは Unix 時刻を30秒で割った値で、30秒ごとに1つ進む。
	 */
	@Test
	void calculatesStep() {
		assertThat(TotpStep.at(Instant.ofEpochSecond(59))).isEqualTo(new TotpStep(1));
		assertThat(TotpStep.at(Instant.ofEpochSecond(60))).isEqualTo(new TotpStep(2));
		assertThat(new TotpStep(2).isAfter(new TotpStep(1))).isTrue();
		assertThat(new TotpStep(2).isAfter(new TotpStep(2))).isFalse();
	}

}
