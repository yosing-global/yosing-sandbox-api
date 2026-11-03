package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;
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
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;

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
	public Optional<User> findById(UserId id) {
		return get(ItemKeys.user(id)).map(UserItemMapper::fromItem);
	}

	@Override
	public Optional<User> countOtpAttempt(UserId id, String loginTokenId) {
		// 読んでから書くのではなく DB の値に加えるため、同時に試されても数え漏れが無い。
		// 今の仮認証で、上限に達していないときだけ数える（数えられた試行だけが照合に進む）
		try {
			Map<String, AttributeValue> updated = client.updateItem(request -> request.tableName(tableName)
				.key(ItemKeys.user(id))
				.updateExpression("ADD " + OTP_FAILURE_COUNT_REF + " :one")
				.conditionExpression(
						LOGIN_TOKEN_ID_REF + " = :loginTokenId AND " + OTP_FAILURE_COUNT_REF + " < :maxAttempts")
				.expressionAttributeNames(Map.of(OTP_FAILURE_COUNT_REF, UserItemMapper.OTP_FAILURE_COUNT,
						LOGIN_TOKEN_ID_REF, UserItemMapper.LOGIN_TOKEN_ID))
				.expressionAttributeValues(Map.of(":one", AttributeValue.fromN("1"), ":loginTokenId",
						AttributeValue.fromS(loginTokenId), ":maxAttempts",
						AttributeValue.fromN(Integer.toString(User.MAX_OTP_FAILURES))))
				.returnValues(ReturnValue.ALL_NEW)).attributes();
			return Optional.of(UserItemMapper.fromItem(updated));
		} catch (ConditionalCheckFailedException ex) {
			return Optional.empty();
		}
	}

	@Override
	public boolean saveOtpLock(User current, User locked) {
		String loginTokenId = current.loginTokenId()
			.orElseThrow(() -> new IllegalArgumentException("仮認証の jti が無い"));
		Instant lockedUntil = locked.lockedUntil()
			.orElseThrow(() -> new IllegalArgumentException("ロックの期限が無い"));
		// 今の仮認証のときだけ、ロックして仮認証を終える（新しい仮認証は消さない）
		try {
			client.updateItem(request -> request.tableName(tableName)
				.key(ItemKeys.user(current.id()))
				.updateExpression("SET " + LOCKED_UNTIL_REF + " = :lockedUntil REMOVE " + LOGIN_TOKEN_ID_REF)
				.conditionExpression(LOGIN_TOKEN_ID_REF + " = :loginTokenId")
				.expressionAttributeNames(Map.of(LOCKED_UNTIL_REF, UserItemMapper.LOCKED_UNTIL, LOGIN_TOKEN_ID_REF,
						UserItemMapper.LOGIN_TOKEN_ID))
				.expressionAttributeValues(Map.of(":lockedUntil", AttributeValue.fromS(lockedUntil.toString()),
						":loginTokenId", AttributeValue.fromS(loginTokenId))));
			return true;
		} catch (ConditionalCheckFailedException ex) {
			// 既に終わっている、または新しい仮認証に置き換わっている
			return false;
		}
	}

	@Override
	public boolean savePasswordAttempt(User current, User counted) {
		Map<String, String> names = new HashMap<>();
		names.put(FAILURE_COUNT_REF, UserItemMapper.PASSWORD_FAILURE_COUNT);
		names.put(OTP_FAILURE_COUNT_REF, UserItemMapper.OTP_FAILURE_COUNT);
		names.put(LOCKED_UNTIL_REF, UserItemMapper.LOCKED_UNTIL);
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":currentCount", AttributeValue.fromN(Integer.toString(current.passwordFailureCount())));
		values.put(":countedCount", AttributeValue.fromN(Integer.toString(counted.passwordFailureCount())));
		values.put(":currentOtpCount", AttributeValue.fromN(Integer.toString(current.otpFailureCount())));
		values.put(":countedOtpCount", AttributeValue.fromN(Integer.toString(counted.otpFailureCount())));

		// 読んだときから失敗回数（パスワード・OTP）・ロックの期限が変わっていないこと（同時の試行は、先に書いた1つだけが通る）。
		// 失敗回数の条件は、項目があることも兼ねる
		String condition = FAILURE_COUNT_REF + " = :currentCount AND " + OTP_FAILURE_COUNT_REF
				+ " = :currentOtpCount AND ";
		if (current.lockedUntil().isPresent()) {
			values.put(":currentLockedUntil", AttributeValue.fromS(current.lockedUntil().get().toString()));
			condition += LOCKED_UNTIL_REF + " = :currentLockedUntil";
		} else {
			condition += "attribute_not_exists(" + LOCKED_UNTIL_REF + ")";
		}
		// OTP の失敗回数は、ロックの期限が過ぎた後の最初の試行でだけ0に戻る
		String update = "SET " + FAILURE_COUNT_REF + " = :countedCount, " + OTP_FAILURE_COUNT_REF + " = :countedOtpCount";
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
				UserItemMapper.PASSWORD_FAILURE_COUNT, LOCKED_UNTIL_REF, UserItemMapper.LOCKED_UNTIL,
				LOGIN_TOKEN_ID_REF, UserItemMapper.LOGIN_TOKEN_ID);
		Map<String, AttributeValue> values = Map.of(":failureCount",
				AttributeValue.fromN(Integer.toString(verified.passwordFailureCount())), ":loginTokenId",
				AttributeValue.fromS(loginTokenId));
		// パスワードの失敗回数を戻してロックを解き、新しい jti に置き換える（前の仮認証の JWT は使えなくなる）。
		// OTP の失敗回数は変えない（OTP が合ったときだけ戻す）
		client.updateItem(request -> request.tableName(tableName)
			.key(ItemKeys.user(verified.id()))
			.updateExpression("SET " + FAILURE_COUNT_REF + " = :failureCount, " + LOGIN_TOKEN_ID_REF
					+ " = :loginTokenId REMOVE " + LOCKED_UNTIL_REF)
			.conditionExpression(ItemKeys.EXISTS)
			.expressionAttributeNames(names)
			.expressionAttributeValues(values));
	}


	private Optional<Map<String, AttributeValue>> get(Map<String, AttributeValue> key) {
		// 認証の判定に使う読み取りは、強い整合性で行う
		GetItemResponse response = client
			.getItem(request -> request.tableName(tableName).key(key).consistentRead(true));
		return response.hasItem() ? Optional.of(response.item()) : Optional.empty();
	}

}
