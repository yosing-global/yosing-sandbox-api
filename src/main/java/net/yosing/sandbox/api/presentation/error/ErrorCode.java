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

	/** ログイン ID が使用済み。 */
	LOGIN_ID_TAKEN(HttpStatus.CONFLICT, "このログイン ID は使われています。"),

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
