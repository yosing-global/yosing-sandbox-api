package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.Map;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.session.SessionIdHash;
import net.yosing.sandbox.api.domain.model.session.SessionRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * セッションの項目の読み書き。
 */
@Repository
class DynamoDbSessionRepository implements SessionRepository {

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbSessionRepository(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public Optional<Session> findByIdHash(SessionIdHash idHash) {
		// 認証の判定に使う読み取りは、強い整合性で行う（ログアウトした直後のセッションを通さない）
		GetItemResponse response = client
			.getItem(request -> request.tableName(tableName).key(ItemKeys.session(idHash)).consistentRead(true));
		return response.hasItem() ? Optional.of(SessionItemMapper.fromItem(idHash, response.item()))
				: Optional.empty();
	}

	@Override
	public boolean saveAccess(Session touched) {
		// 同時にログアウトされた（削除された）セッションを、作り直さない
		try {
			client.updateItem(request -> request.tableName(tableName)
				.key(ItemKeys.session(touched.idHash()))
				.updateExpression("SET #lastAccessedAt = :lastAccessedAt, #expiresAt = :expiresAt")
				.conditionExpression(ItemKeys.EXISTS)
				.expressionAttributeNames(Map.of(ItemKeys.PK_REF, ItemKeys.PK, "#lastAccessedAt",
						SessionItemMapper.LAST_ACCESSED_AT, "#expiresAt", SessionItemMapper.EXPIRES_AT))
				.expressionAttributeValues(Map.of(":lastAccessedAt",
						AttributeValue.fromS(touched.lastAccessedAt().toString()), ":expiresAt",
						AttributeValue.fromN(Long.toString(touched.expiresAt().getEpochSecond())))));
			return true;
		} catch (ConditionalCheckFailedException ex) {
			// 同時にログアウトされた
			return false;
		}
	}

	@Override
	public void delete(SessionIdHash idHash) {
		client.deleteItem(request -> request.tableName(tableName).key(ItemKeys.session(idHash)));
	}

}
