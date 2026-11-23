package net.yosing.sandbox.api.presentation.session;

import net.yosing.sandbox.api.application.session.GetCurrentUserUseCase;
import net.yosing.sandbox.api.application.session.LogoutUseCase;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * セッションの API（Notion の 4.2 API設計の 5・6）。セッション ID は {@code Authorization: Bearer} で受け取る。
 */
@RestController
class SessionController {

	private final GetCurrentUserUseCase getCurrentUser;

	private final LogoutUseCase logout;

	SessionController(GetCurrentUserUseCase getCurrentUser, LogoutUseCase logout) {
		this.getCurrentUser = getCurrentUser;
		this.logout = logout;
	}

	/**
	 * ログイン中のユーザーの情報。
	 */
	@GetMapping("/v1/me")
	MeResponse me(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		return MeResponse.from(getCurrentUser.get(BearerSessionId.parse(authorization)));
	}

	/**
	 * ログアウト。
	 */
	@DeleteMapping("/v1/sessions/current")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		logout.logout(BearerSessionId.parse(authorization));
	}

}
