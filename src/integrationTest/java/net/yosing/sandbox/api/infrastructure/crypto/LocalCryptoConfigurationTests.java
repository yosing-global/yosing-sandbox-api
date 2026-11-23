package net.yosing.sandbox.api.infrastructure.crypto;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * local のプロファイルで、ローカルの暗号化（application-local.yml の鍵）が使われることを確かめる。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class LocalCryptoConfigurationTests {

	@Autowired
	TotpSecretEncryptor encryptor;

	/**
	 * 暗号化の Bean がローカルの実装で、設定の鍵で暗号化・復号できる。
	 */
	@Test
	void usesLocalEncryptor() {
		UserId userId = UserId.generate();
		TotpSecret secret = new TotpSecret(new byte[] { 1, 2, 3 });

		assertThat(encryptor).isInstanceOf(LocalTotpSecretEncryptor.class);
		assertThat(encryptor.decrypt(encryptor.encrypt(secret, userId), userId)).isEqualTo(secret);
	}

}
