package net.yosing.sandbox.api.infrastructure.crypto;

import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.spec.SecretKeySpec;

import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.infrastructure.environment.EnvironmentProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

/**
 * KMS の代わりに Java 標準の暗号を使う（local のプロファイル：手元の開発・結合テスト）。
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalCryptoConfiguration {

	// AES-256
	private static final int AES_KEY_BYTES = 32;

	@Bean
	TotpSecretEncryptor totpSecretEncryptor(LocalCryptoProperties properties, EnvironmentProperties environment) {
		String encodedKey = properties.totpSecretKey();
		Assert.hasText(encodedKey, "sandbox.local-crypto.totp-secret-key が無い");
		byte[] key = Base64.getDecoder().decode(encodedKey);
		Assert.isTrue(key.length == AES_KEY_BYTES, "sandbox.local-crypto.totp-secret-key は 256 ビットにする");
		return new LocalTotpSecretEncryptor(new SecretKeySpec(key, "AES"), environment.roleEnv(), new SecureRandom());
	}

}
