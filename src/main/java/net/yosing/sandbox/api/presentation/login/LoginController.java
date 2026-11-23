package net.yosing.sandbox.api.presentation.login;

import jakarta.validation.Valid;
import net.yosing.sandbox.api.application.login.StartLoginCommand;
import net.yosing.sandbox.api.application.login.StartLoginUseCase;
import net.yosing.sandbox.api.application.login.VerifyLoginCommand;
import net.yosing.sandbox.api.application.login.VerifyLoginUseCase;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ログインの API（Notion の 4.2 API設計の 3・4）。
 */
@RestController
@RequestMapping("/v1/logins")
class LoginController {

	private final StartLoginUseCase startLogin;

	private final VerifyLoginUseCase verifyLogin;

	LoginController(StartLoginUseCase startLogin, VerifyLoginUseCase verifyLogin) {
		this.startLogin = startLogin;
		this.verifyLogin = verifyLogin;
	}

	/**
	 * ログインの開始。
	 */
	@PostMapping
	StartLoginResponse start(@Valid @RequestBody StartLoginRequest request) {
		// 形式は検証済みのため、ドメインの型にできる
		StartLoginCommand command = new StartLoginCommand(LoginId.of(request.loginId()),
				new Password(request.password()));
		return StartLoginResponse.from(this.startLogin.start(command));
	}

	/**
	 * ログインの確定。
	 */
	@PostMapping("/verify")
	VerifyLoginResponse verify(@Valid @RequestBody VerifyLoginRequest request) {
		VerifyLoginCommand command = new VerifyLoginCommand(request.loginToken(), new OtpCode(request.code()));
		return VerifyLoginResponse.from(this.verifyLogin.verify(command));
	}

}
