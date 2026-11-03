package net.yosing.sandbox.api.infrastructure.crypto;

import java.util.Map;

import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;

/**
 * KMS の対称鍵で暗号化する。
 *
 * <p>暗号化コンテキストに userId と環境（env）を必ず渡す（IAM の条件で、渡さないと拒否される）。
 * 復号でも鍵の ARN を指定する（タスクロールは、この鍵だけを許可している）。
 */
final class KmsTotpSecretEncryptor implements TotpSecretEncryptor {

	private final KmsClient kms;

	private final String keyArn;

	private final String roleEnv;

	KmsTotpSecretEncryptor(KmsClient kms, String keyArn, String roleEnv) {
		this.kms = kms;
		this.keyArn = keyArn;
		this.roleEnv = roleEnv;
	}

	@Override
	public EncryptedTotpSecret encrypt(TotpSecret secret, UserId userId) {
		EncryptRequest request = EncryptRequest.builder()
			.keyId(keyArn)
			.plaintext(SdkBytes.fromByteArray(secret.bytes()))
			.encryptionContext(encryptionContext(userId))
			.build();
		return new EncryptedTotpSecret(kms.encrypt(request).ciphertextBlob().asByteArray());
	}

	@Override
	public TotpSecret decrypt(EncryptedTotpSecret encrypted, UserId userId) {
		DecryptRequest request = DecryptRequest.builder()
			.keyId(keyArn)
			.ciphertextBlob(SdkBytes.fromByteArray(encrypted.bytes()))
			.encryptionContext(encryptionContext(userId))
			.build();
		return new TotpSecret(kms.decrypt(request).plaintext().asByteArray());
	}

	private Map<String, String> encryptionContext(UserId userId) {
		return Map.of("userId", userId.toString(), "env", roleEnv);
	}

}
