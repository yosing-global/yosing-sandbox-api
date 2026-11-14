package net.yosing.sandbox.api.infrastructure.dynamodb;

import net.yosing.sandbox.api.infrastructure.aws.AwsProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

/**
 * DynamoDB のクライアント。
 */
@Configuration(proxyBeanMethods = false)
class DynamoDbConfiguration {

	@Bean
	DynamoDbClient dynamoDbClient(AwsProperties awsProperties, DynamoDbProperties properties) {
		DynamoDbClientBuilder builder = DynamoDbClient.builder().region(Region.of(awsProperties.region()));
		if (properties.endpoint() != null) {
			// DynamoDB Local（-sharedDb で起動する）は認証情報を確かめないため、固定の値を渡す
			//   PR 環境でも、タスクロールの認証情報を DynamoDB Local に渡さない
			builder.endpointOverride(properties.endpoint())
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
		}
		return builder.build();
	}

}
