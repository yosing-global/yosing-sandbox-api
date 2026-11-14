package net.yosing.sandbox.api.infrastructure.crypto;

import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.infrastructure.aws.AwsProperties;
import net.yosing.sandbox.api.infrastructure.environment.EnvironmentProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.Assert;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * KMS を使う暗号化（aws のプロファイル：pr / dev / test / stg / prod）。
 */
@Configuration(proxyBeanMethods = false)
@Profile("aws")
class KmsCryptoConfiguration {

	@Bean
	KmsClient kmsClient(AwsProperties awsProperties) {
		return KmsClient.builder().region(Region.of(awsProperties.region())).build();
	}

	@Bean
	TotpSecretEncryptor totpSecretEncryptor(KmsClient kmsClient, KmsProperties kmsProperties,
			EnvironmentProperties environment) {
		String keyArn = kmsProperties.totpSecretKeyArn();
		Assert.hasText(keyArn, "sandbox.kms.totp-secret-key-arn（環境変数 KMS_KEY_ARN）が無い");
		return new KmsTotpSecretEncryptor(kmsClient, keyArn, environment.roleEnv());
	}

}
