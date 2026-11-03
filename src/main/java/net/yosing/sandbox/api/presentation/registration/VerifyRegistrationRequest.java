package net.yosing.sandbox.api.presentation.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;

/**
 * 登録の確定の要求。形式の規則はドメインの型（{@link OtpCode}）と同じものを使う。
 *
 * @param code 認証アプリに表示された OTP コード
 */
record VerifyRegistrationRequest(
		@NotNull(message = "必須です。")
		@Pattern(regexp = OtpCode.PATTERN, message = "数字6桁で入力してください。") String code) {

	@Override
	public String toString() {
		return "VerifyRegistrationRequest[code=****]";
	}

}
