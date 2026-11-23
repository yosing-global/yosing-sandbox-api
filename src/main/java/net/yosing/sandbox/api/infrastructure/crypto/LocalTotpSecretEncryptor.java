package net.yosing.sandbox.api.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * KMS の代わりに、Java 標準の AES-GCM で暗号化する（local のプロファイル）。
 *
 * <p>KMS の暗号化コンテキストと同じく、userId と環境を AAD（追加の認証データ）に入れ、別のユーザー・環境では復号できないようにする。
 * 暗号文は「IV（12バイト）＋暗号文とタグ」。IV は暗号化のたびにランダムに作る。
 */
final class LocalTotpSecretEncryptor implements TotpSecretEncryptor {

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private final SecretKey key;

	private final String roleEnv;

	// SecureRandom はスレッドセーフ（Bean で共有してよい）
	private final SecureRandom random;

	LocalTotpSecretEncryptor(SecretKey key, String roleEnv, SecureRandom random) {
		this.key = key;
		this.roleEnv = roleEnv;
		this.random = random;
	}

	@Override
	public EncryptedTotpSecret encrypt(TotpSecret secret, UserId userId) {
		byte[] iv = new byte[IV_BYTES];
		this.random.nextBytes(iv);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(additionalData(userId));
			byte[] ciphertext = cipher.doFinal(secret.bytes());
			return new EncryptedTotpSecret(
					ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("暗号化に失敗した", ex);
		}
	}

	@Override
	public TotpSecret decrypt(EncryptedTotpSecret encrypted, UserId userId) {
		byte[] bytes = encrypted.bytes();
		if (bytes.length <= IV_BYTES) {
			throw new IllegalStateException("暗号文が短すぎる");
		}
		byte[] iv = Arrays.copyOfRange(bytes, 0, IV_BYTES);
		byte[] ciphertext = Arrays.copyOfRange(bytes, IV_BYTES, bytes.length);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, this.key, new GCMParameterSpec(TAG_BITS, iv));
			cipher.updateAAD(additionalData(userId));
			return new TotpSecret(cipher.doFinal(ciphertext));
		}
		catch (GeneralSecurityException ex) {
			// ユーザー・環境の違い、改ざん、鍵の違い（AEADBadTagException）等
			throw new IllegalStateException("復号に失敗した", ex);
		}
	}

	private byte[] additionalData(UserId userId) {
		return ("env=" + this.roleEnv + ";userId=" + userId).getBytes(StandardCharsets.UTF_8);
	}

}
