package net.yosing.sandbox.api.infrastructure.dynamodb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * DynamoDB Local に、起動時にテーブルを作る（dynamodb-local のプロファイルだけ）。
 *
 * <p>DynamoDB Local（-inMemory）は起動のたびにデータが消えるため、毎回作る。
 * テーブルの定義は aws-infra-env の products/sandbox/nonprod/dynamodb.tf と揃える（PK / SK は文字列、TTL は expiresAt）。
 * dev / test / stg / prod では作らない（テーブルは Terraform が作り、タスクロールにも作成の権限が無い）。
 */
@Component
@Profile("dynamodb-local")
class DynamoDbLocalTableInitializer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(DynamoDbLocalTableInitializer.class);

	// PR 環境では DynamoDB Local（サイドカー）の起動を待つ（1秒おきに最大30回）
	private static final int MAX_ATTEMPTS = 30;

	private static final long RETRY_INTERVAL_MILLIS = 1000;

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbLocalTableInitializer(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public void run(ApplicationArguments args) throws InterruptedException {
		for (int attempt = 1;; attempt++) {
			try {
				createTableIfNotExists();
				return;
			}
			catch (SdkClientException ex) {
				if (attempt >= MAX_ATTEMPTS) {
					throw ex;
				}
				LOGGER.info("DynamoDB Local に接続できないため待つ（{}/{}）", attempt, MAX_ATTEMPTS);
				Thread.sleep(RETRY_INTERVAL_MILLIS);
			}
		}
	}

	private void createTableIfNotExists() {
		try {
			this.client.describeTable((request) -> request.tableName(this.tableName));
			LOGGER.info("テーブルがあるため作らない：{}", this.tableName);
			return;
		}
		catch (ResourceNotFoundException ex) {
			// 無ければ作る
		}
		this.client.createTable((request) -> request.tableName(this.tableName)
			.attributeDefinitions(
					AttributeDefinition.builder().attributeName("PK").attributeType(ScalarAttributeType.S).build(),
					AttributeDefinition.builder().attributeName("SK").attributeType(ScalarAttributeType.S).build())
			.keySchema(KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
					KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
			.billingMode(BillingMode.PAY_PER_REQUEST));
		this.client.waiter().waitUntilTableExists((request) -> request.tableName(this.tableName));
		this.client.updateTimeToLive((request) -> request.tableName(this.tableName)
			.timeToLiveSpecification((ttl) -> ttl.attributeName("expiresAt").enabled(true)));
		LOGGER.info("テーブルを作った：{}", this.tableName);
	}

}
