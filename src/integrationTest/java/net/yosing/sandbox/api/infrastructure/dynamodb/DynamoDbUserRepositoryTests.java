package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * ユーザーとログイン ID の項目の読み書きを、DynamoDB Local で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbUserRepositoryTests {

	@Autowired
	DynamoDbUserRepository repository;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * ログイン ID の項目（LOGINID#&lt;loginId&gt; / LOGINID）があれば true、無ければ false を返す。
	 */
	@Test
	void existsByLoginId() {
		dynamoDb.putItem(request -> request.tableName(properties.tableName())
			.item(Map.of("PK", AttributeValue.fromS("LOGINID#dummy_exists"), "SK", AttributeValue.fromS("LOGINID"),
					"userId", AttributeValue.fromS("00000000-0000-0000-0000-000000000000"))));

		assertThat(repository.existsByLoginId(LoginId.of("dummy_exists"))).isTrue();
		assertThat(repository.existsByLoginId(LoginId.of("dummy_missing"))).isFalse();
	}

}
