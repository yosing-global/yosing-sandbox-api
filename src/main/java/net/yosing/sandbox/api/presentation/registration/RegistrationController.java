package net.yosing.sandbox.api.presentation.registration;

import jakarta.validation.Valid;
import net.yosing.sandbox.api.application.registration.StartRegistrationCommand;
import net.yosing.sandbox.api.application.registration.StartRegistrationUseCase;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import org.springframework.http.HttpStatus;
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

	RegistrationController(StartRegistrationUseCase startRegistration) {
		this.startRegistration = startRegistration;
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
		return StartRegistrationResponse.from(startRegistration.start(command));
	}

}
