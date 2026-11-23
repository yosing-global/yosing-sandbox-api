package net.yosing.sandbox.api.presentation.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Pattern.Flag;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;

/**
 * 登録の開始の要求。形式の規則はドメインの型（{@link LoginId}・{@link Password}）と同じものを使う。
 *
 * @param loginId ログイン ID（大文字は小文字にそろえる）
 * @param password パスワード
 */
record StartRegistrationRequest(
		@NotNull(message = "必須です。") @Pattern(regexp = LoginId.PATTERN, flags = Flag.CASE_INSENSITIVE,
				message = "3〜32文字の英字・数字・_・- で入力してください。") String loginId,
		@NotNull(message = "必須です。") @Pattern(regexp = Password.PATTERN,
				message = "12〜64文字の半角の英字・数字・記号・空白で入力してください。") String password) {

	@Override
	public String toString() {
		return "StartRegistrationRequest[loginId=" + this.loginId + ", password=****]";
	}

}
