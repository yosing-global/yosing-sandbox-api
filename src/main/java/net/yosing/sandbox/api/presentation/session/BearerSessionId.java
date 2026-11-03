package net.yosing.sandbox.api.presentation.session;

import java.util.Locale;

import net.yosing.sandbox.api.application.session.SessionInvalidException;
import net.yosing.sandbox.api.domain.model.session.SessionId;

/**
 * {@code Authorization: Bearer <sessionId>} のヘッダーから、セッション ID を取り出す。
 *
 * <p>ヘッダーが無い・Bearer でない・セッション ID の形が違うときは、無効なセッションとして扱う（401 {@code SESSION_INVALID}）。
 * 方式の名前（Bearer）は、大文字と小文字を区別しない（RFC 7235）。
 */
final class BearerSessionId {

	private static final String PREFIX = "bearer ";

	private BearerSessionId() {
	}

	/**
	 * @param authorization {@code Authorization} ヘッダーの値（無ければ null）
	 * @return セッション ID
	 * @throws SessionInvalidException ヘッダーが無い、または形が違う場合
	 */
	static SessionId parse(String authorization) {
		if (authorization == null || authorization.length() <= PREFIX.length()
				|| !authorization.substring(0, PREFIX.length()).toLowerCase(Locale.ROOT).equals(PREFIX)) {
			throw new SessionInvalidException();
		}
		return SessionId.fromClient(authorization.substring(PREFIX.length())).orElseThrow(SessionInvalidException::new);
	}

}
