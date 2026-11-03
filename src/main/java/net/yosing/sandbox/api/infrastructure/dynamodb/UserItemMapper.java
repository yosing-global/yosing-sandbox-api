package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.HashMap;
import java.util.Map;

import net.yosing.sandbox.api.domain.model.user.User;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * ユーザーと、DynamoDB の項目（ユーザー：{@code USER#<userId>} / {@code PROFILE}、ログイン ID：{@code LOGINID#<loginId>} /
 * {@code LOGINID}）の変換。
 *
 * <p>ユーザーの属性（キーのほか）：
 * <ul>
 * <li>userId（S）、loginId（S）、passwordHash（S）</li>
 * <li>totpSecret（B）：暗号化した TOTP の秘密情報</li>
 * <li>lastUsedTotpStep（N）：最後に使った TOTP のステップ</li>
 * <li>passwordFailureCount（N）・otpFailureCount（N）：失敗回数</li>
 * <li>lockedUntil（S）：パスワードのロックの期限（ISO 8601 の UTC。ロックしていなければ属性なし）</li>
 * <li>loginTokenId（S）：今の仮認証の jti（仮認証していなければ属性なし）</li>
 * <li>createdAt（S）：ISO 8601 の UTC</li>
 * </ul>
 * ログイン ID の属性：userId（S）。
 */
final class UserItemMapper {

	static final String USER_ID = "userId";

	static final String LOGIN_ID = "loginId";

	static final String PASSWORD_HASH = "passwordHash";

	static final String TOTP_SECRET = "totpSecret";

	static final String LAST_USED_TOTP_STEP = "lastUsedTotpStep";

	static final String PASSWORD_FAILURE_COUNT = "passwordFailureCount";

	static final String OTP_FAILURE_COUNT = "otpFailureCount";

	static final String LOCKED_UNTIL = "lockedUntil";

	static final String LOGIN_TOKEN_ID = "loginTokenId";

	static final String CREATED_AT = "createdAt";

	private UserItemMapper() {
	}

	static Map<String, AttributeValue> toItem(User user) {
		Map<String, AttributeValue> item = new HashMap<>(ItemKeys.user(user.id()));
		item.put(USER_ID, AttributeValue.fromS(user.id().toString()));
		item.put(LOGIN_ID, AttributeValue.fromS(user.loginId().value()));
		item.put(PASSWORD_HASH, AttributeValue.fromS(user.passwordHash().value()));
		item.put(TOTP_SECRET, AttributeValue.fromB(SdkBytes.fromByteArray(user.totpSecret().bytes())));
		item.put(LAST_USED_TOTP_STEP, AttributeValue.fromN(Long.toString(user.lastUsedTotpStep().value())));
		item.put(PASSWORD_FAILURE_COUNT, AttributeValue.fromN(Integer.toString(user.passwordFailureCount())));
		item.put(OTP_FAILURE_COUNT, AttributeValue.fromN(Integer.toString(user.otpFailureCount())));
		user.lockedUntil().ifPresent(lockedUntil -> item.put(LOCKED_UNTIL, AttributeValue.fromS(lockedUntil.toString())));
		user.loginTokenId().ifPresent(tokenId -> item.put(LOGIN_TOKEN_ID, AttributeValue.fromS(tokenId)));
		item.put(CREATED_AT, AttributeValue.fromS(user.createdAt().toString()));
		return item;
	}

	static Map<String, AttributeValue> toLoginIdItem(User user) {
		Map<String, AttributeValue> item = new HashMap<>(ItemKeys.loginId(user.loginId()));
		item.put(USER_ID, AttributeValue.fromS(user.id().toString()));
		return item;
	}

}
