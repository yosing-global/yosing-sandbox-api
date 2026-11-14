package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.TableDescription;

/**
 * DynamoDB Local に、起動時にテーブルが作られることを確認する。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbLocalTableInitializerTests {

	@Autowired
	DynamoDbClient client;

	@Autowired
	DynamoDbProperties properties;

	@Autowired
	DynamoDbLocalTableInitializer initializer;

	/**
	 * 起動時に、キー（PK・SK）と TTL の属性（expiresAt）を設定したテーブルが作られる。
	 */
	@Test
	void createsTableOnStartup() {
		TableDescription table = client.describeTable(request -> request.tableName(properties.tableName())).table();

		assertThat(table.keySchema()).containsExactly(
				KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
				KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build());
		assertThat(client.describeTimeToLive(request -> request.tableName(properties.tableName()))
			.timeToLiveDescription()
			.attributeName()).isEqualTo("expiresAt");
	}

	/**
	 * テーブルがあるときに、もう一度動かしても失敗しない（PR 環境で api だけ再起動したとき等）。
	 */
	@Test
	void skipsWhenTableExists() throws InterruptedException {
		initializer.run(new DefaultApplicationArguments());

		assertThat(client.listTables().tableNames()).containsOnlyOnce(properties.tableName());
	}

}
