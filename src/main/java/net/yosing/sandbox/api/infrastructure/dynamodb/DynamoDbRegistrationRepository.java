package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.Map;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.registration.RegistrationRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

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
		// 同じキーの項目を上書きしない（あれば ConditionalCheckFailedException）
		client.putItem(request -> request.tableName(tableName)
			.item(RegistrationItemMapper.toItem(registration))
			.conditionExpression(ItemKeys.NOT_EXISTS)
			.expressionAttributeNames(ItemKeys.PK_NAMES));
	}

	@Override
	public Optional<Registration> findById(RegistrationId id) {
		// 認証の判定に使う読み取りは、強い整合性で行う
		GetItemResponse response = client
			.getItem(request -> request.tableName(tableName).key(ItemKeys.registration(id)).consistentRead(true));
		return response.hasItem() ? Optional.of(RegistrationItemMapper.fromItem(response.item())) : Optional.empty();
	}

	@Override
	public Optional<Registration> countOtpAttempt(RegistrationId id) {
		// 読んでから書くのではなく DB の値に加えるため、同時に試されても数え漏れが無い。
		// 上限に達していないときだけ数える（数えられた試行だけが照合に進む）。回数の条件は、仮登録があることも兼ねる
		try {
			Map<String, AttributeValue> updated = client.updateItem(request -> request.tableName(tableName)
				.key(ItemKeys.registration(id))
				.updateExpression("ADD #count :one")
				.conditionExpression("#count < :maxAttempts")
				.expressionAttributeNames(Map.of("#count", RegistrationItemMapper.OTP_FAILURE_COUNT))
				.expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1"), ":maxAttempts",
						AttributeValue.fromN(Integer.toString(Registration.MAX_OTP_FAILURES))))
				.returnValues(ReturnValue.ALL_NEW)).attributes();
			return Optional.of(RegistrationItemMapper.fromItem(updated));
		} catch (ConditionalCheckFailedException ex) {
			// 仮登録が無い（同時に削除・確定された）、または上限に達した
			return Optional.empty();
		}
	}

	@Override
	public void delete(RegistrationId id) {
		client.deleteItem(request -> request.tableName(tableName).key(ItemKeys.registration(id)));
	}

}
