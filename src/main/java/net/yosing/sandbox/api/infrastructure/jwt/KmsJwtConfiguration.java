package net.yosing.sandbox.api.infrastructure.jwt;

import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.infrastructure.crypto.KmsProperties;
import net.yosing.sandbox.api.infrastructure.environment.EnvironmentProperties;
import software.amazon.awssdk.services.kms.KmsClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;

/**
 * KMS の HMAC の鍵で仮認証 JWT に署名する（aws のプロファイル：pr / dev / test / stg / prod）。
 *
 * <p>KmsClient は infrastructure.crypto の設定で作ったものを使う。
 */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
class KmsJwtConfiguration {

	@Bean
	LoginTokenSigner loginTokenSigner(KmsClient kmsClient, KmsProperties kmsProperties,
			EnvironmentProperties environment) {
		String keyArn = kmsProperties.jwtKeyArn();
		Assert.hasText(keyArn, "sandbox.kms.jwt-key-arn（環境変数 JWT_KMS_KEY_ARN）が無い");
		return new NimbusLoginTokenSigner(new KmsMacSigner(kmsClient, keyArn), new KmsMacVerifier(kmsClient, keyArn),
				environment.appEnv());
	}

}
