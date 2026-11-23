package net.yosing.sandbox.api.infrastructure.jwt;

import java.util.Base64;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.infrastructure.crypto.LocalCryptoProperties;
import net.yosing.sandbox.api.infrastructure.environment.EnvironmentProperties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

/**
 * KMS の代わりに Java 標準の HmacSHA256 で仮認証 JWT に署名する（local のプロファイル：手元の開発・結合テスト）。
 */
@Configuration(proxyBeanMethods = false)
@Profile("local")
class LocalJwtConfiguration {

	// HS256 の鍵は 256 ビット以上（KMS の HMAC_256 と揃える）
	private static final int HMAC_KEY_BYTES = 32;

	@Bean
	LoginTokenSigner loginTokenSigner(LocalCryptoProperties properties, EnvironmentProperties environment)
			throws JOSEException {
		String encodedKey = properties.jwtKey();
		Assert.hasText(encodedKey, "sandbox.local-crypto.jwt-key が無い");
		byte[] key = Base64.getDecoder().decode(encodedKey);
		Assert.isTrue(key.length == HMAC_KEY_BYTES, "sandbox.local-crypto.jwt-key は 256 ビットにする");
		return new NimbusLoginTokenSigner(new MACSigner(key), new MACVerifier(key), environment.appEnv());
	}

}
