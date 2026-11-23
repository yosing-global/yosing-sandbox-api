package net.yosing.sandbox.api.presentation.registration;

import jakarta.validation.Valid;
import net.yosing.sandbox.api.application.registration.RegistrationNotFoundException;
import net.yosing.sandbox.api.application.registration.StartRegistrationCommand;
import net.yosing.sandbox.api.application.registration.StartRegistrationUseCase;
import net.yosing.sandbox.api.application.registration.VerifyRegistrationCommand;
import net.yosing.sandbox.api.application.registration.VerifyRegistrationUseCase;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登録の API（Notion の 4.2 API設計の 1・2）。
 */
@RestController
@RequestMapping("/v1/registrations")
class RegistrationController {

	private final StartRegistrationUseCase startRegistration;

	private final VerifyRegistrationUseCase verifyRegistration;

	RegistrationController(StartRegistrationUseCase startRegistration, VerifyRegistrationUseCase verifyRegistration) {
		this.startRegistration = startRegistration;
		this.verifyRegistration = verifyRegistration;
	}

	/**
	 * 登録の開始。
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	StartRegistrationResponse start(@Valid @RequestBody StartRegistrationRequest request) {
		// 形式は検証済みのため、ドメインの型にできる
		StartRegistrationCommand command = new StartRegistrationCommand(LoginId.of(request.loginId()),
				new Password(request.password()));
		return StartRegistrationResponse.from(this.startRegistration.start(command));
	}

	/**
	 * 登録の確定。
	 */
	@PostMapping("/{registrationId}/verify")
	@ResponseStatus(HttpStatus.CREATED)
	VerifyRegistrationResponse verify(@PathVariable String registrationId,
			@Valid @RequestBody VerifyRegistrationRequest request) {
		VerifyRegistrationCommand command = new VerifyRegistrationCommand(parseRegistrationId(registrationId),
				new OtpCode(request.code()));
		return VerifyRegistrationResponse.from(this.verifyRegistration.verify(command));
	}

	// UUID の形でない登録 ID は、無い登録 ID と同じ 404 にする
	private static RegistrationId parseRegistrationId(String value) {
		try {
			return RegistrationId.parse(value);
		}
		catch (IllegalArgumentException ex) {
			throw new RegistrationNotFoundException();
		}
	}

}
