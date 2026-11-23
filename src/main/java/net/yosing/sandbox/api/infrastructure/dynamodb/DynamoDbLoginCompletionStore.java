package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.List;
import java.util.Map;

import net.yosing.sandbox.api.application.port.LoginCompletionStore;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.User;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import org.springframework.stereotype.Repository;

/**
 * ログインの確定の保存（AP9）。TransactWriteItems で、次の2つをすべて行うか、何もしない。
 *
 * <ol>
 * <li>ユーザーの更新：最後に使ったステップを一致したステップにし、OTP の失敗回数を0に戻し、仮認証の jti を消す。
 * 条件は、jti が読んだときと同じこと（仮認証が置き換わっていない）と、最後に使ったステップが一致したステップより前であること
 * （同じコードを同時に使われても、1つしか通らない）</li>
 * <li>セッションの作成（無いこと）</li>
 * </ol>
 * ユーザーの条件に合わなかったことは、取り消しの理由（CancellationReason。上の順に並ぶ）で見分ける。
 */
@Repository
class DynamoDbLoginCompletionStore implements LoginCompletionStore {

	private static final int USER_INDEX = 0;

	private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";

	private static final String STEP_REF = "#lastUsedTotpStep";

	private static final String OTP_FAILURE_COUNT_REF = "#otpFailureCount";

	private static final String LOGIN_TOKEN_ID_REF = "#loginTokenId";

	private final DynamoDbClient client;

	private final String tableName;

	DynamoDbLoginCompletionStore(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	@Override
	public boolean complete(User current, User verified, Session session) {
		String loginTokenId = current.loginTokenId().orElseThrow(() -> new IllegalArgumentException("仮認証の jti が無い"));
		Update userUpdate = Update.builder()
			.tableName(tableName)
			.key(ItemKeys.user(current.id()))
			.updateExpression("SET " + STEP_REF + " = :matchedStep, " + OTP_FAILURE_COUNT_REF + " = :otpFailureCount"
					+ " REMOVE " + LOGIN_TOKEN_ID_REF)
			.conditionExpression(LOGIN_TOKEN_ID_REF + " = :loginTokenId AND " + STEP_REF + " < :matchedStep")
			.expressionAttributeNames(Map.of(STEP_REF, UserItemMapper.LAST_USED_TOTP_STEP, OTP_FAILURE_COUNT_REF,
					UserItemMapper.OTP_FAILURE_COUNT, LOGIN_TOKEN_ID_REF, UserItemMapper.LOGIN_TOKEN_ID))
			.expressionAttributeValues(
					Map.of(":matchedStep", AttributeValue.fromN(Long.toString(verified.lastUsedTotpStep().value())),
							":otpFailureCount", AttributeValue.fromN(Integer.toString(verified.otpFailureCount())),
							":loginTokenId", AttributeValue.fromS(loginTokenId)))
			.build();
		Put sessionPut = Put.builder()
			.tableName(tableName)
			.item(SessionItemMapper.toItem(session))
			.conditionExpression(ItemKeys.NOT_EXISTS)
			.expressionAttributeNames(ItemKeys.PK_NAMES)
			.build();
		List<TransactWriteItem> items = List.of(TransactWriteItem.builder().update(userUpdate).build(),
				TransactWriteItem.builder().put(sessionPut).build());
		try {
			client.transactWriteItems(request -> request.transactItems(items));
			return true;
		}
		catch (TransactionCanceledException ex) {
			if (userConditionFailed(ex)) {
				return false;
			}
			throw ex;
		}
	}

	private static boolean userConditionFailed(TransactionCanceledException ex) {
		List<CancellationReason> reasons = ex.cancellationReasons();
		return ex.hasCancellationReasons() && reasons.size() > USER_INDEX
				&& CONDITIONAL_CHECK_FAILED.equals(reasons.get(USER_INDEX).code());
	}

}
