package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.Map;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * 仮登録の項目の読み書き。
 */
@Repository
class DynamoDbRegistrationRepository implements RegistrationRepository {

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbRegistrationRepository(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public void add(Registration registration) {
		// 同じキーの項目を上書きしない（無ければ ConditionalCheckFailedException）
		PutItemRequest request = PutItemRequest.builder()
			.tableName(tableName)
			.item(RegistrationItemMapper.toItem(registration))
			.conditionExpression("attribute_not_exists(#pk)")
			.expressionAttributeNames(Map.of("#pk", ItemKeys.PK))
			.build();
		client.putItem(request);
	}

}
