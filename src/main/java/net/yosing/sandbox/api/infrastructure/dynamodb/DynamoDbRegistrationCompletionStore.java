package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.List;
import java.util.Map;

import net.yosing.sandbox.api.application.port.RegistrationCompletionStore;
import net.yosing.sandbox.api.application.registration.LoginIdTakenException;
import net.yosing.sandbox.api.application.registration.RegistrationNotFoundException;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.User;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Delete;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import org.springframework.stereotype.Repository;

/**
 * 登録の確定の保存（AP5）。TransactWriteItems で、次の4つをすべて行うか、何もしない。
 *
 * <ol>
 * <li>ユーザーの作成（無いこと）</li>
 * <li>ログイン ID の作成（無いこと。同じログイン ID の同時の本登録を防ぐ）</li>
 * <li>仮登録の削除（あること。同じ仮登録の二重の確定を防ぐ）</li>
 * <li>セッションの作成（無いこと）</li>
 * </ol>
 * 条件に合わなかったものは、取り消しの理由（CancellationReason。上の順に並ぶ）で見分ける。
 */
@Repository
class DynamoDbRegistrationCompletionStore implements RegistrationCompletionStore {

	private static final int LOGIN_ID_INDEX = 1;

	private static final int REGISTRATION_INDEX = 2;

	private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbRegistrationCompletionStore(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public void complete(Registration registration, User user, Session session) {
		List<TransactWriteItem> items = List.of(putIfNotExists(UserItemMapper.toItem(user)),
				putIfNotExists(UserItemMapper.toLoginIdItem(user)),
				TransactWriteItem.builder()
					.delete(Delete.builder()
						.tableName(this.tableName)
						.key(ItemKeys.registration(registration.id()))
						.conditionExpression(ItemKeys.EXISTS)
						.expressionAttributeNames(ItemKeys.PK_NAMES)
						.build())
					.build(),
				putIfNotExists(SessionItemMapper.toItem(session)));
		try {
			this.client.transactWriteItems((request) -> request.transactItems(items));
		}
		catch (TransactionCanceledException ex) {
			if (conditionFailed(ex, LOGIN_ID_INDEX)) {
				throw new LoginIdTakenException();
			}
			if (conditionFailed(ex, REGISTRATION_INDEX)) {
				throw new RegistrationNotFoundException();
			}
			throw ex;
		}
	}

	private TransactWriteItem putIfNotExists(Map<String, AttributeValue> item) {
		return TransactWriteItem.builder()
			.put(Put.builder()
				.tableName(this.tableName)
				.item(item)
				.conditionExpression(ItemKeys.NOT_EXISTS)
				.expressionAttributeNames(ItemKeys.PK_NAMES)
				.build())
			.build();
	}

	private static boolean conditionFailed(TransactionCanceledException ex, int index) {
		List<CancellationReason> reasons = ex.cancellationReasons();
		return ex.hasCancellationReasons() && reasons.size() > index
				&& CONDITIONAL_CHECK_FAILED.equals(reasons.get(index).code());
	}

}
