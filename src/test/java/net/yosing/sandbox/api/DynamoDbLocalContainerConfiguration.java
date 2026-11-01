package net.yosing.sandbox.api;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * テストで使う DynamoDB Local（Testcontainers）。local のプロファイルと一緒に使う。
 *
 * <p>イメージは compose.yaml・PR 環境のサイドカー（deploy/task-def.jsonnet）と揃える。
 */
@TestConfiguration(proxyBeanMethods = false)
public class DynamoDbLocalContainerConfiguration {

	// 3.3.2 のダイジェスト（Testcontainers はタグとダイジェストを同時に書けないため、ダイジェストだけで指定する）
	private static final String IMAGE = "public.ecr.aws/aws-dynamodb-local/aws-dynamodb-local"
			+ "@sha256:6ada631ec7aa5fc83446855eb17233e3dad184fec47477e8140c6c569cfe0c86";

	private static final int PORT = 8000;

	@Bean
	GenericContainer<?> dynamoDbLocal() {
		return new GenericContainer<>(DockerImageName.parse(IMAGE)).withExposedPorts(PORT)
			.withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb");
	}

	// application-dynamodb-local.yml の接続先（localhost:8000）を、コンテナの接続先で上書きする
	@Bean
	DynamicPropertyRegistrar dynamoDbLocalProperties(GenericContainer<?> dynamoDbLocal) {
		return registry -> registry.add("sandbox.dynamodb.endpoint",
				() -> "http://" + dynamoDbLocal.getHost() + ":" + dynamoDbLocal.getMappedPort(PORT));
	}

}
