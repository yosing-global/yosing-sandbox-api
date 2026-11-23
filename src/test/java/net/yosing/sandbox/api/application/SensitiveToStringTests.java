package net.yosing.sandbox.api.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import net.yosing.sandbox.api.application.login.StartLoginCommand;
import net.yosing.sandbox.api.application.login.StartLoginResult;
import net.yosing.sandbox.api.application.login.VerifyLoginCommand;
import net.yosing.sandbox.api.application.login.VerifyLoginResult;
import net.yosing.sandbox.api.application.registration.StartRegistrationCommand;
import net.yosing.sandbox.api.application.registration.StartRegistrationResult;
import net.yosing.sandbox.api.application.registration.VerifyRegistrationCommand;
import net.yosing.sandbox.api.application.registration.VerifyRegistrationResult;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpauthUri;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ユースケースの入出力の型が、toString（ログ・例外のメッセージ等）に秘密の値を出さないことを確かめる。
 *
 * <p>秘密の値：パスワード・OTP コード・設定 URI（TOTP の秘密情報を含む）・セッション ID・仮認証 JWT。
 * 入出力の型は record のため、toString は中の型（{@link Password} 等）の toString か、型ごとの toString で隠す。
 */
class SensitiveToStringTests {

	private static final String PASSWORD = "dummy correct horse battery";

	private static final String CODE = "123456";

	private static final String LOGIN_TOKEN = "dummy.login.token";

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	/**
	 * 登録開始の入力にパスワードを、結果に設定 URI（秘密情報）を出さない。
	 */
	@Test
	void hidesRegistrationStartSecrets() {
		OtpauthUri uri = OtpauthUri.create("dummy-sandbox", "dummy_alice",
				new TotpSecret("dummy-totp-secret-20".getBytes(StandardCharsets.US_ASCII)));

		assertThat(new StartRegistrationCommand(LoginId.of("dummy_alice"), new Password(PASSWORD)).toString())
			.doesNotContain(PASSWORD);
		assertThat(new StartRegistrationResult(RegistrationId.generate(), uri, NOW).toString())
			.doesNotContain(uri.value(), "secret=");
	}

	/**
	 * 登録確定の入力に OTP コードを、結果にセッション ID を出さない。
	 */
	@Test
	void hidesRegistrationVerifySecrets() {
		SessionId sessionId = SessionId.generate();

		assertThat(new VerifyRegistrationCommand(RegistrationId.generate(), new OtpCode(CODE)).toString())
			.doesNotContain(CODE);
		assertThat(new VerifyRegistrationResult(sessionId, NOW).toString()).doesNotContain(sessionId.value());
	}

	/**
	 * ログイン開始の入力にパスワードを、結果に仮認証 JWT を出さない。
	 */
	@Test
	void hidesLoginStartSecrets() {
		assertThat(new StartLoginCommand(LoginId.of("dummy_alice"), new Password(PASSWORD)).toString())
			.doesNotContain(PASSWORD);
		assertThat(new StartLoginResult(LOGIN_TOKEN, NOW).toString()).doesNotContain(LOGIN_TOKEN);
	}

	/**
	 * ログイン確定の入力に仮認証 JWT と OTP コードを、結果にセッション ID を出さない。
	 */
	@Test
	void hidesLoginVerifySecrets() {
		SessionId sessionId = SessionId.generate();

		assertThat(new VerifyLoginCommand(LOGIN_TOKEN, new OtpCode(CODE)).toString()).doesNotContain(LOGIN_TOKEN, CODE);
		assertThat(new VerifyLoginResult(sessionId, NOW).toString()).doesNotContain(sessionId.value());
	}

}
