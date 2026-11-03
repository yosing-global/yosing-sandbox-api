package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.domain.model.user.UserRepository;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * ユーザーとログイン ID の項目の読み書き。
 */
@Repository
class DynamoDbUserRepository implements UserRepository {

	// パスワードの失敗回数を指す名前。「password」を含めると、Find Security Bugs がハードコードされたパスワード
	// （HARD_CODE_PASSWORD）と誤検出するため、含めない
	private static final String FAILURE_COUNT_REF = "#failureCount";

	private static final String OTP_FAILURE_COUNT_REF = "#otpFailureCount";

	private static final String LOCKED_UNTIL_REF = "#lockedUntil";

	private static final String LOGIN_TOKEN_ID_REF = "#loginTokenId";

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

	@Override
	public Optional<User> findByLoginId(LoginId loginId) {
		// ログイン ID の項目 → ユーザーの項目の順に読む（GSI は使わない）
		return get(ItemKeys.loginId(loginId)).map(UserItemMapper::userIdOfLoginIdItem)
			.flatMap(this::findById)
			.filter(user -> user.loginId().equals(loginId));
	}

	@Override
	public boolean savePasswordAttempt(User current, User counted) {
		Map<String, String> names = new HashMap<>();
		names.put(FAILURE_COUNT_REF, UserItemMapper.PASSWORD_FAILURE_COUNT);
		names.put(LOCKED_UNTIL_REF, UserItemMapper.LOCKED_UNTIL);
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":currentCount", AttributeValue.fromN(Integer.toString(current.passwordFailureCount())));
		values.put(":countedCount", AttributeValue.fromN(Integer.toString(counted.passwordFailureCount())));

		// 読んだときから失敗回数・ロックの期限が変わっていないこと（同時の試行は、先に書いた1つだけが通る）。
		// 失敗回数の条件は、項目があることも兼ねる
		String condition = FAILURE_COUNT_REF + " = :currentCount AND ";
		if (current.lockedUntil().isPresent()) {
			values.put(":currentLockedUntil", AttributeValue.fromS(current.lockedUntil().get().toString()));
			condition += LOCKED_UNTIL_REF + " = :currentLockedUntil";
		} else {
			condition += "attribute_not_exists(" + LOCKED_UNTIL_REF + ")";
		}
		String update = "SET " + FAILURE_COUNT_REF + " = :countedCount";
		if (counted.lockedUntil().isPresent()) {
			values.put(":countedLockedUntil", AttributeValue.fromS(counted.lockedUntil().get().toString()));
			update += ", " + LOCKED_UNTIL_REF + " = :countedLockedUntil";
		} else {
			update += " REMOVE " + LOCKED_UNTIL_REF;
		}
		String conditionExpression = condition;
		String updateExpression = update;

		try {
			client.updateItem(request -> request.tableName(tableName)
				.key(ItemKeys.user(current.id()))
				.updateExpression(updateExpression)
				.conditionExpression(conditionExpression)
				.expressionAttributeNames(names)
				.expressionAttributeValues(values));
			return true;
		} catch (ConditionalCheckFailedException ex) {
			return false;
		}
	}

	@Override
	public void savePasswordVerified(User verified) {
		String loginTokenId = verified.loginTokenId()
			.orElseThrow(() -> new IllegalArgumentException("仮認証の jti が無い"));
		Map<String, String> names = Map.of(ItemKeys.PK_REF, ItemKeys.PK, FAILURE_COUNT_REF,
				UserItemMapper.PASSWORD_FAILURE_COUNT, OTP_FAILURE_COUNT_REF, UserItemMapper.OTP_FAILURE_COUNT,
				LOCKED_UNTIL_REF, UserItemMapper.LOCKED_UNTIL, LOGIN_TOKEN_ID_REF, UserItemMapper.LOGIN_TOKEN_ID);
		Map<String, AttributeValue> values = Map.of(":failureCount",
				AttributeValue.fromN(Integer.toString(verified.passwordFailureCount())), ":otpFailureCount",
				AttributeValue.fromN(Integer.toString(verified.otpFailureCount())), ":loginTokenId",
				AttributeValue.fromS(loginTokenId));
		// 失敗回数を戻してロックを解き、新しい jti に置き換える（前の仮認証の JWT は使えなくなる）
		client.updateItem(request -> request.tableName(tableName)
			.key(ItemKeys.user(verified.id()))
			.updateExpression("SET " + FAILURE_COUNT_REF + " = :failureCount, " + OTP_FAILURE_COUNT_REF
					+ " = :otpFailureCount, " + LOGIN_TOKEN_ID_REF + " = :loginTokenId REMOVE " + LOCKED_UNTIL_REF)
			.conditionExpression(ItemKeys.EXISTS)
			.expressionAttributeNames(names)
			.expressionAttributeValues(values));
	}

	private Optional<User> findById(UserId id) {
		return get(ItemKeys.user(id)).map(UserItemMapper::fromItem);
	}

	private Optional<Map<String, AttributeValue>> get(Map<String, AttributeValue> key) {
		// 認証の判定に使う読み取りは、強い整合性で行う
		GetItemResponse response = client
			.getItem(request -> request.tableName(tableName).key(key).consistentRead(true));
		return response.hasItem() ? Optional.of(response.item()) : Optional.empty();
	}

}
