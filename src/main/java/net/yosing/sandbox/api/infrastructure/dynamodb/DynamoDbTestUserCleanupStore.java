package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.yosing.sandbox.api.application.port.TestUserCleanupStore;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

/**
 * テストのユーザーの掃除の、探す・消す（{@link TestUserCleanupStore}）。
 *
 * <ul>
 * <li>探す：Scan。PK が {@code USER#} で始まり、loginId が先頭に合うユーザーの項目を読む（ID・ログイン ID・登録した日時だけ）。
 * テーブル全体を読むが、掃除は CI から一度だけ動かすため許容する（タスクロールの Scan の権限は、このためだけにある）</li>
 * <li>消す：TransactWriteItems で、ユーザーの項目とログイン ID の項目をまとめて消す。条件は、ユーザーの項目のログイン ID が
 * 同じで {@link TestUserCleanupStore#TEST_LOGIN_ID_PREFIX} で始まることと、ログイン ID の項目がこのユーザーを指すこと</li>
 * </ul>
 */
@Repository
class DynamoDbTestUserCleanupStore implements TestUserCleanupStore {

	private static final String USER_PK_PREFIX = "USER#";

	private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

	private static final String USER_ID_REF = "#userId";

	private static final String LOGIN_ID_REF = "#loginId";

	private static final String CREATED_AT_REF = "#createdAt";

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbTestUserCleanupStore(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public List<TestUser> findByLoginIdPrefix(String loginIdPrefix) {
		requireTestPrefix(loginIdPrefix);
		ScanRequest request = ScanRequest.builder()
			.tableName(tableName)
			.filterExpression("begins_with(" + ItemKeys.PK_REF + ", :userPkPrefix) AND begins_with(" + LOGIN_ID_REF
					+ ", :loginIdPrefix)")
			.projectionExpression(USER_ID_REF + ", " + LOGIN_ID_REF + ", " + CREATED_AT_REF)
			.expressionAttributeNames(Map.of(ItemKeys.PK_REF, ItemKeys.PK, USER_ID_REF, UserItemMapper.USER_ID,
					LOGIN_ID_REF, UserItemMapper.LOGIN_ID, CREATED_AT_REF, UserItemMapper.CREATED_AT))
			.expressionAttributeValues(Map.of(":userPkPrefix", AttributeValue.fromS(USER_PK_PREFIX), ":loginIdPrefix",
					AttributeValue.fromS(loginIdPrefix)))
			.build();
		List<TestUser> users = new ArrayList<>();
		// 1回の Scan で返るのは 1MB まで。続きはページを順に読む
		for (Map<String, AttributeValue> item : client.scanPaginator(request).items()) {
			users.add(new TestUser(UserId.parse(item.get(UserItemMapper.USER_ID).s()),
					new LoginId(item.get(UserItemMapper.LOGIN_ID).s()),
					Instant.parse(item.get(UserItemMapper.CREATED_AT).s())));
		}
		return users;
	}

	@Override
	public boolean delete(TestUser user) {
		requireTestPrefix(user.loginId().value());
		Delete userDelete = Delete.builder()
			.tableName(tableName)
			.key(ItemKeys.user(user.id()))
			.conditionExpression(LOGIN_ID_REF + " = :loginId AND begins_with(" + LOGIN_ID_REF + ", :loginIdPrefix)")
			.expressionAttributeNames(Map.of(LOGIN_ID_REF, UserItemMapper.LOGIN_ID))
			.expressionAttributeValues(Map.of(":loginId", AttributeValue.fromS(user.loginId().value()),
					":loginIdPrefix", AttributeValue.fromS(TEST_LOGIN_ID_PREFIX)))
			.build();
		Delete loginIdDelete = Delete.builder()
			.tableName(tableName)
			.key(ItemKeys.loginId(user.loginId()))
			.conditionExpression(USER_ID_REF + " = :userId")
			.expressionAttributeNames(Map.of(USER_ID_REF, UserItemMapper.USER_ID))
			.expressionAttributeValues(Map.of(":userId", AttributeValue.fromS(user.id().toString())))
			.build();
		List<TransactWriteItem> items = List.of(TransactWriteItem.builder().delete(userDelete).build(),
				TransactWriteItem.builder().delete(loginIdDelete).build());
		try {
			client.transactWriteItems(request -> request.transactItems(items));
			return true;
		} catch (TransactionCanceledException ex) {
			if (conditionFailed(ex)) {
				return false;
			}
			throw ex;
		}
	}

	// テストのユーザーでないものを、探したり消したりしない（呼び出しの誤りで、ふつうのユーザーを消さないように）
	private static void requireTestPrefix(String value) {
		if (!value.startsWith(TEST_LOGIN_ID_PREFIX)) {
			throw new IllegalArgumentException("テストのユーザーのログイン ID ではない");
		}
	}

	private static boolean conditionFailed(TransactionCanceledException ex) {
		return ex.hasCancellationReasons() && ex.cancellationReasons()
			.stream()
			.map(CancellationReason::code)
			.anyMatch(CONDITIONAL_CHECK_FAILED::equals);
	}

}
