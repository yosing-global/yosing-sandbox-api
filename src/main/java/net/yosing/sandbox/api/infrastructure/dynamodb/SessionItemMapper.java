package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.session.SessionIdHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * セッションと、DynamoDB の項目（{@code SESSION#<セッション ID のハッシュ>} / {@code SESSION}）の変換。
 *
 * <p>属性（キーのほか）：
 * <ul>
 * <li>userId（S）</li>
 * <li>createdAt（S）・lastAccessedAt（S）：ISO 8601 の UTC</li>
 * <li>expiresAt（N）：期限（Unix 時刻の秒）。テーブルの TTL の属性</li>
 * </ul>
 * セッション ID の元の値は保存しない。
 */
final class SessionItemMapper {

	static final String USER_ID = "userId";

	static final String CREATED_AT = "createdAt";

	static final String LAST_ACCESSED_AT = "lastAccessedAt";

	static final String EXPIRES_AT = "expiresAt";

	private SessionItemMapper() {
	}

	static Map<String, AttributeValue> toItem(Session session) {
		Map<String, AttributeValue> item = new HashMap<>(ItemKeys.session(session.idHash()));
		item.put(USER_ID, AttributeValue.fromS(session.userId().toString()));
		item.put(CREATED_AT, AttributeValue.fromS(session.createdAt().toString()));
		item.put(LAST_ACCESSED_AT, AttributeValue.fromS(session.lastAccessedAt().toString()));
		item.put(EXPIRES_AT, AttributeValue.fromN(Long.toString(session.expiresAt().getEpochSecond())));
		return item;
	}

	/**
	 * 項目からセッションを戻す。期限（expiresAt）は、発行と最終操作の日時から計算し直す（保存した値と同じになる）。
	 */
	static Session fromItem(SessionIdHash idHash, Map<String, AttributeValue> item) {
		return Session.restore(idHash, UserId.parse(item.get(USER_ID).s()), Instant.parse(item.get(CREATED_AT).s()),
				Instant.parse(item.get(LAST_ACCESSED_AT).s()));
	}

}
