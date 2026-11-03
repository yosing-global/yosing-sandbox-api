package net.yosing.sandbox.api.infrastructure.dynamodb;

import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * ユーザーとログイン ID の項目の読み書き。
 */
@Repository
class DynamoDbUserRepository implements UserRepository {

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbUserRepository(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public boolean existsByLoginId(LoginId loginId) {
		// 認証の判定に使う読み取りは、強い整合性で行う。キーだけ取れば足りる
		GetItemRequest request = GetItemRequest.builder()
			.tableName(tableName)
			.key(ItemKeys.loginId(loginId))
			.consistentRead(true)
			.projectionExpression(ItemKeys.PK_REF)
			.expressionAttributeNames(ItemKeys.PK_NAMES)
			.build();
		return client.getItem(request).hasItem();
	}

}
