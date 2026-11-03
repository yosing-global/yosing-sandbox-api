package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 仮登録と、DynamoDB の項目（{@code REG#<registrationId>} / {@code REG}）の変換。
 *
 * <p>属性（キーのほか）：
 * <ul>
 * <li>registrationId（S）、userId（S）、loginId（S）、passwordHash（S）</li>
 * <li>totpSecret（B）：暗号化した TOTP の秘密情報</li>
 * <li>otpFailureCount（N）：OTP の失敗回数</li>
 * <li>createdAt（S）：ISO 8601 の UTC</li>
 * <li>expiresAt（N）：期限（Unix 時刻の秒）。テーブルの TTL の属性</li>
 * </ul>
 */
final class RegistrationItemMapper {

	static final String REGISTRATION_ID = "registrationId";

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
		item.put(REGISTRATION_ID, AttributeValue.fromS(registration.id().toString()));
		item.put(USER_ID, AttributeValue.fromS(registration.userId().toString()));
		item.put(LOGIN_ID, AttributeValue.fromS(registration.loginId().value()));
		item.put(PASSWORD_HASH, AttributeValue.fromS(registration.passwordHash().value()));
		item.put(TOTP_SECRET, AttributeValue.fromB(SdkBytes.fromByteArray(registration.totpSecret().bytes())));
		item.put(OTP_FAILURE_COUNT, AttributeValue.fromN(Integer.toString(registration.otpFailureCount())));
		item.put(CREATED_AT, AttributeValue.fromS(registration.createdAt().toString()));
		item.put(EXPIRES_AT, AttributeValue.fromN(Long.toString(registration.expiresAt().getEpochSecond())));
		return item;
	}

	static Registration fromItem(Map<String, AttributeValue> item) {
		return Registration.restore(RegistrationId.parse(item.get(REGISTRATION_ID).s()),
				UserId.parse(item.get(USER_ID).s()), new LoginId(item.get(LOGIN_ID).s()),
				new PasswordHash(item.get(PASSWORD_HASH).s()),
				new EncryptedTotpSecret(item.get(TOTP_SECRET).b().asByteArray()),
				Integer.parseInt(item.get(OTP_FAILURE_COUNT).n()), Instant.parse(item.get(CREATED_AT).s()),
				Instant.ofEpochSecond(Long.parseLong(item.get(EXPIRES_AT).n())));
	}

}
