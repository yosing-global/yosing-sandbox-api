package net.yosing.sandbox.api.infrastructure.crypto;

import java.util.Map;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * KMS の呼び出し方（鍵の ARN・暗号化コンテキスト）を確かめる。実際の KMS での動きは PR 環境で確かめる。
 */
class KmsTotpSecretEncryptorTests {

	private static final String KEY_ARN = "arn:aws:kms:ap-northeast-1:000000000000:key/dummy";

	private final KmsClient kms = mock(KmsClient.class);

	private final KmsTotpSecretEncryptor encryptor = new KmsTotpSecretEncryptor(kms, KEY_ARN, "dummy-pr");

	/**
	 * 暗号化で、鍵の ARN・秘密情報・暗号化コンテキスト（userId と環境名）を KMS に渡し、KMS の返した暗号文を返す。
	 */
	@Test
	void encryptsWithKeyAndEncryptionContext() {
		UserId userId = UserId.generate();
		when(kms.encrypt(any(EncryptRequest.class)))
			.thenReturn(EncryptResponse.builder().ciphertextBlob(SdkBytes.fromByteArray(new byte[] { 9, 9 })).build());

		EncryptedTotpSecret encrypted = encryptor.encrypt(new TotpSecret(new byte[] { 1, 2, 3 }), userId);

		ArgumentCaptor<EncryptRequest> request = ArgumentCaptor.forClass(EncryptRequest.class);
		verify(kms).encrypt(request.capture());
		assertThat(request.getValue().keyId()).isEqualTo(KEY_ARN);
		assertThat(request.getValue().plaintext().asByteArray()).containsExactly(1, 2, 3);
		assertThat(request.getValue().encryptionContext())
			.isEqualTo(Map.of("userId", userId.toString(), "env", "dummy-pr"));
		assertThat(encrypted).isEqualTo(new EncryptedTotpSecret(new byte[] { 9, 9 }));
	}

	/**
	 * 復号でも、鍵の ARN・暗号文・暗号化コンテキストを KMS に渡し、KMS の返した秘密情報を返す。
	 */
	@Test
	void decryptsWithKeyAndEncryptionContext() {
		UserId userId = UserId.generate();
		when(kms.decrypt(any(DecryptRequest.class)))
			.thenReturn(DecryptResponse.builder().plaintext(SdkBytes.fromByteArray(new byte[] { 1, 2, 3 })).build());

		TotpSecret secret = encryptor.decrypt(new EncryptedTotpSecret(new byte[] { 9, 9 }), userId);

		ArgumentCaptor<DecryptRequest> request = ArgumentCaptor.forClass(DecryptRequest.class);
		verify(kms).decrypt(request.capture());
		assertThat(request.getValue().keyId()).isEqualTo(KEY_ARN);
		assertThat(request.getValue().ciphertextBlob().asByteArray()).containsExactly(9, 9);
		assertThat(request.getValue().encryptionContext())
			.isEqualTo(Map.of("userId", userId.toString(), "env", "dummy-pr"));
		assertThat(secret).isEqualTo(new TotpSecret(new byte[] { 1, 2, 3 }));
	}

}
