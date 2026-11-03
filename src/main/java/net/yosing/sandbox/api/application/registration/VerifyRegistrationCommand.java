package net.yosing.sandbox.api.application.registration;

import java.util.Objects;

import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;

/**
 * 登録の確定の入力。
 *
 * @param registrationId 登録 ID
 * @param code 入力された OTP コード
 */
public record VerifyRegistrationCommand(RegistrationId registrationId, OtpCode code) {

	public VerifyRegistrationCommand {
		Objects.requireNonNull(registrationId, "registrationId");
		Objects.requireNonNull(code, "code");
	}

}
