package net.yosing.sandbox.api.presentation.error;

import org.springframework.http.HttpStatus;

/**
 * エラー応答の {@code code}（BFF が処理を分けるための値）と、その status・detail（Notion の 4.2 API設計）。
 *
 * <p>API を作るときに、使う値を足していく。detail に内部の情報（例外の内容等）は入れない。
 */
enum ErrorCode {

	/** 入力の形式の誤り。 */
	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "入力の形式が正しくありません。"),

	/** ログイン ID かパスワードが違う（どちらが違うか、ロック中かは返さない）。 */
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "ログイン ID かパスワードが正しくありません。"),

	/** OTP コードが違う、または使用済み。 */
	OTP_INVALID(HttpStatus.UNAUTHORIZED, "OTP コードが正しくありません。"),

	/** 仮認証 JWT が無効・期限切れ・使用済み。 */
	LOGIN_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "仮認証が無効か、期限が切れています。ログインを最初からやり直してください。"),

	/** 登録 ID が無い、または期限切れ。 */
	REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "登録が見つからないか、期限が切れています。最初からやり直してください。"),

	/** ログイン ID が使用済み。 */
	LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "このログイン ID は使われています。"),

	/** OTP の失敗が上限に達した。 */
	OTP_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "OTP コードを続けて間違えたため、最初からやり直してください。"),

	/** 想定外のエラー。 */
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "想定外のエラーが発生しました。");

	private final HttpStatus status;

	private final String detail;

	ErrorCode(HttpStatus status, String detail) {
		this.status = status;
		this.detail = detail;
	}

	HttpStatus status() {
		return status;
	}

	String detail() {
		return detail;
	}

}
