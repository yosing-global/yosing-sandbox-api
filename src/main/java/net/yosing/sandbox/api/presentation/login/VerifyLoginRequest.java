package net.yosing.sandbox.api.presentation.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;

/**
 * ログイン確定の要求。コードの形式の規則はドメインの型（{@link OtpCode}）と同じものを使う。
 *
 * <p>仮認証 JWT の形式（署名・期限等）は、ここでは確かめず、ユースケースで検証する（不正なら 401）。
 *
 * @param loginToken ログインの開始で受け取った仮認証 JWT
 * @param code 認証アプリに表示された OTP コード
 */
record VerifyLoginRequest(@NotBlank(message = "必須です。") String loginToken,
		@NotNull(message = "必須です。") @Pattern(regexp = OtpCode.PATTERN, message = "数字6桁で入力してください。") String code) {

	@Override
	public String toString() {
		return "VerifyLoginRequest[loginToken=****, code=****]";
	}

}
