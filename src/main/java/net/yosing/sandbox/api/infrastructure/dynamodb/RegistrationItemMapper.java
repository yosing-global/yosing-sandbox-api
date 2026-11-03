package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.HashMap;
import java.util.Map;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 仮登録と、DynamoDB の項目（{@code REG#<registrationId>} / {@code REG}）の変換。
 *
 * <p>属性（キーのほか）：
 * <ul>
 * <li>userId（S）、loginId（S）、passwordHash（S）</li>
 * <li>totpSecret（B）：暗号化した TOTP の秘密情報</li>
 * <li>otpFailureCount（N）：OTP の失敗回数</li>
 * <li>createdAt（S）：ISO 8601 の UTC</li>
 * <li>expiresAt（N）：期限（Unix 時刻の秒）。テーブルの TTL の属性</li>
 * </ul>
 */
final class RegistrationItemMapper {

	static final String USER_ID = "userId";

	static final String LOGIN_ID = "loginId";

	static final String PASSWORD_HASH = "passwordHash";

	static final String TOTP_SECRET = "totpSecret";

	static final String OTP_FAILURE_COUNT = "otpFailureCount";

	static final String CREATED_AT = "createdAt";

	static final String EXPIRES_AT = "expiresAt";

	private RegistrationItemMapper() {
	}

	static Map<String, AttributeValue> toItem(Registration registration) {
		Map<String, AttributeValue> item = new HashMap<>(ItemKeys.registration(registration.id()));
		item.put(USER_ID, AttributeValue.fromS(registration.userId().toString()));
		item.put(LOGIN_ID, AttributeValue.fromS(registration.loginId().value()));
		item.put(PASSWORD_HASH, AttributeValue.fromS(registration.passwordHash().value()));
		item.put(TOTP_SECRET, AttributeValue.fromB(SdkBytes.fromByteArray(registration.totpSecret().bytes())));
		item.put(OTP_FAILURE_COUNT, AttributeValue.fromN(Integer.toString(registration.otpFailureCount())));
		item.put(CREATED_AT, AttributeValue.fromS(registration.createdAt().toString()));
		item.put(EXPIRES_AT, AttributeValue.fromN(Long.toString(registration.expiresAt().getEpochSecond())));
		return item;
	}

}
