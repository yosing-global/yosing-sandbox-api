package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

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
		new DynamoDbTestItems(dynamoDb, properties).putLoginId(LoginId.of("dummy_exists"), UserId.generate());

		assertThat(repository.existsByLoginId(LoginId.of("dummy_exists"))).isTrue();
		assertThat(repository.existsByLoginId(LoginId.of("dummy_missing"))).isFalse();
	}

}
