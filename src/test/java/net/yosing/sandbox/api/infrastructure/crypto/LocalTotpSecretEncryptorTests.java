package net.yosing.sandbox.api.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import javax.crypto.spec.SecretKeySpec;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * ローカルの暗号化（AES-GCM）で、暗号化と復号ができ、ユーザー・環境・改ざんの違いを検出できることを確かめる。
 */
class LocalTotpSecretEncryptorTests {

	private static final TotpSecret SECRET = new TotpSecret("dummy-totp-secret".getBytes(StandardCharsets.US_ASCII));

	// AES-256 の鍵（32バイト）
	private final SecretKeySpec key = new SecretKeySpec(
			"dummy-aes-key-0123456789abcdefgh".getBytes(StandardCharsets.US_ASCII), "AES");

	private final LocalTotpSecretEncryptor encryptor = new LocalTotpSecretEncryptor(key, "dummy-local",
			new SecureRandom());

	/**
	 * 暗号化したものを、同じユーザーで復号すると元に戻る。
	 */
	@Test
	void decryptsWhatItEncrypted() {
		UserId userId = UserId.generate();

		EncryptedTotpSecret encrypted = encryptor.encrypt(SECRET, userId);

		assertThat(encryptor.decrypt(encrypted, userId)).isEqualTo(SECRET);
	}

	/**
	 * IV を毎回ランダムに作るため、同じ秘密情報でも暗号文は毎回変わる。
	 */
	@Test
	void producesDifferentCiphertextEachTime() {
		UserId userId = UserId.generate();

		assertThat(encryptor.encrypt(SECRET, userId)).isNotEqualTo(encryptor.encrypt(SECRET, userId));
	}

	/**
	 * 別のユーザーとしては復号できない（userId を AAD に入れているため）。
	 */
	@Test
	void cannotDecryptForAnotherUser() {
		EncryptedTotpSecret encrypted = encryptor.encrypt(SECRET, UserId.generate());

		assertThatIllegalStateException().isThrownBy(() -> encryptor.decrypt(encrypted, UserId.generate()));
	}

	/**
	 * 別の環境では復号できない（環境名を AAD に入れているため）。
	 */
	@Test
	void cannotDecryptInAnotherEnvironment() {
		UserId userId = UserId.generate();
		EncryptedTotpSecret encrypted = encryptor.encrypt(SECRET, userId);
		LocalTotpSecretEncryptor anotherEnvironment = new LocalTotpSecretEncryptor(key, "dummy-dev",
				new SecureRandom());

		assertThatIllegalStateException().isThrownBy(() -> anotherEnvironment.decrypt(encrypted, userId));
	}

	/**
	 * 暗号文を1ビットでも書き換えると、復号に失敗する（GCM のタグで検出する）。
	 */
	@Test
	void cannotDecryptTamperedCiphertext() {
		UserId userId = UserId.generate();
		byte[] bytes = encryptor.encrypt(SECRET, userId).bytes();
		bytes[bytes.length - 1] ^= 1;

		assertThatIllegalStateException().isThrownBy(() -> encryptor.decrypt(new EncryptedTotpSecret(bytes), userId));
	}

}
