package net.yosing.sandbox.api.domain.model.shared.totp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * TOTP の秘密情報が、中身を漏らさず、外から書き換えられないことを確かめる。
 */
class TotpSecretTests {

	/**
	 * toString（ログ等）に中身を出さない。
	 */
	@Test
	void doesNotShowValueInToString() {
		TotpSecret secret = new TotpSecret(new byte[] { 1, 2, 3 });

		assertThat(secret.toString()).doesNotContain("1", "2", "3");
	}

	/**
	 * 外から渡した配列・取り出した配列を書き換えても、中身は変わらない。
	 */
	@Test
	void keepsCopyOfBytes() {
		byte[] bytes = { 1, 2, 3 };
		TotpSecret secret = new TotpSecret(bytes);

		bytes[0] = 9;
		secret.bytes()[1] = 9;

		assertThat(secret.bytes()).containsExactly(1, 2, 3);
	}

	/**
	 * 中身が同じなら等しく、違えば等しくない。
	 */
	@Test
	void equalsBySameBytes() {
		assertThat(new TotpSecret(new byte[] { 1, 2, 3 })).isEqualTo(new TotpSecret(new byte[] { 1, 2, 3 }))
			.isNotEqualTo(new TotpSecret(new byte[] { 1, 2, 4 }));
	}

	/**
	 * ランダムに作る秘密情報は20バイト（160 ビット）で、毎回違う。
	 */
	@Test
	void generatesRandomSecret() {
		TotpSecret secret = TotpSecret.generate();

		assertThat(secret.bytes()).hasSize(20);
		assertThat(secret).isNotEqualTo(TotpSecret.generate());
	}

	/**
	 * 空の秘密情報は受け付けない。
	 */
	@Test
	void rejectsEmptyBytes() {
		assertThatIllegalArgumentException().isThrownBy(() -> new TotpSecret(new byte[0]));
	}

}
