package net.yosing.sandbox.api.application.session;

/**
 * セッションが無い・無効・期限切れ（{@code Authorization} が無い・形が違うときを含む）。API では 401 {@code SESSION_INVALID} にする。
 */
public class SessionInvalidException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public SessionInvalidException() {
		super("セッションが無効");
	}

}
