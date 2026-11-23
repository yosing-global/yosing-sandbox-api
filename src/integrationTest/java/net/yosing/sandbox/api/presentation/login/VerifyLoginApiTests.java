package net.yosing.sandbox.api.presentation.login;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.domain.service.TotpVerifier;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbProperties;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbTestItems;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ログインの確定（POST /v1/logins/verify）の API を、DynamoDB Local・ローカルの暗号化と署名で確かめる。
 *
 * <p>ユーザーは DB に直接作り（最後に使ったステップは5ステップ前）、仮認証 JWT はログインの開始の API で受け取る。
 * 正しいコードは、秘密情報から計算する（認証アプリの代わり）。テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class VerifyLoginApiTests {

	private static final String PASSWORD = "dummy correct horse battery";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties dynamoDbProperties;

	@Autowired
	PasswordHasher passwordHasher;

	@Autowired
	TotpSecretEncryptor totpSecretEncryptor;

	@Autowired
	TotpVerifier totpVerifier;

	@Autowired
	Clock clock;

	private final TotpSecret secret = TotpSecret.generate();

	/**
	 * 正しいコードなら 200 でセッション ID と期限を返し、セッションを作って、一致したステップを保存し仮認証を終える。
	 */
	@Test
	void completesLogin() throws Exception {
		User user = putUser("dummy_login_verify_ok");
		String loginToken = startLogin("dummy_login_verify_ok");

		String body = verify(loginToken, validCode()).andExpect(status().isOk())
			.andExpect(jsonPath("$.sessionExpiresAt").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		SessionId sessionId = new SessionId(JsonPath.read(body, "$.sessionId"));
		assertThat(items().sessionItem(sessionId.hash())).isPresent();
		assertThat(items().user(user.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.lastUsedTotpStep().isAfter(user.lastUsedTotpStep())).isTrue();
			assertThat(saved.otpFailureCount()).isZero();
			assertThat(saved.loginTokenId()).isEmpty();
		});
	}

	/**
	 * 確定した後に同じ仮認証 JWT を使うと 401 LOGIN_TOKEN_INVALID。
	 */
	@Test
	void rejectsUsedLoginToken() throws Exception {
		putUser("dummy_login_verify_twice");
		String loginToken = startLogin("dummy_login_verify_twice");
		verify(loginToken, validCode()).andExpect(status().isOk());

		verify(loginToken, validCode()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_TOKEN_INVALID"));
	}

	/**
	 * ログインに使ったコードは、新しい仮認証でも 401 OTP_INVALID（同じコードの再利用を防ぐ）。
	 */
	@Test
	void rejectsReusedCode() throws Exception {
		putUser("dummy_login_verify_reuse");
		OtpCode code = validCode();
		verify(startLogin("dummy_login_verify_reuse"), code).andExpect(status().isOk());

		verify(startLogin("dummy_login_verify_reuse"), code).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("OTP_INVALID"));
	}

	/**
	 * コードが違えば 401 OTP_INVALID で、残りの回数を返す。
	 */
	@Test
	void rejectsWrongCode() throws Exception {
		putUser("dummy_login_verify_wrong");

		verify(startLogin("dummy_login_verify_wrong"), wrongCode()).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("OTP_INVALID"))
			.andExpect(jsonPath("$.remainingAttempts").value(4));
	}

	/**
	 * 5回続けて間違えると 429 OTP_ATTEMPTS_EXCEEDED でアカウントをロックし、仮認証を終える。その後は正しいコードでも
	 * 401 LOGIN_TOKEN_INVALID、正しいパスワードでもログインの開始は 401 INVALID_CREDENTIALS。
	 */
	@Test
	void locksAccountAfterFiveFailures() throws Exception {
		User user = putUser("dummy_login_verify_limit");
		String loginToken = startLogin("dummy_login_verify_limit");
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			verify(loginToken, wrongCode()).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.remainingAttempts").value(User.MAX_OTP_FAILURES - attempt));
		}

		verify(loginToken, wrongCode()).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
		verify(loginToken, validCode()).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_TOKEN_INVALID"));
		startLoginRequest("dummy_login_verify_limit").andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		assertThat(items().user(user.id())).hasValueSatisfying((saved) -> assertThat(saved.lockedUntil()).isPresent());
	}

	/**
	 * OTP の失敗は、ログインを始め直しても数え続ける（4回間違えて始め直すと、次の1回でロック）。
	 */
	@Test
	void countsFailuresAcrossLogins() throws Exception {
		putUser("dummy_login_verify_restart");
		String firstToken = startLogin("dummy_login_verify_restart");
		for (int attempt = 1; attempt < User.MAX_OTP_FAILURES; attempt++) {
			verify(firstToken, wrongCode()).andExpect(status().isUnauthorized());
		}

		verify(startLogin("dummy_login_verify_restart"), wrongCode()).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
	}

	/**
	 * JWT でない仮認証は 401 LOGIN_TOKEN_INVALID。
	 */
	@Test
	void rejectsMalformedLoginToken() throws Exception {
		verify("dummy-not-a-jwt", new OtpCode("123456")).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("LOGIN_TOKEN_INVALID"));
	}

	/**
	 * 仮認証が無い・コードが数字6桁でない入力は 400 VALIDATION_FAILED。
	 */
	@Test
	void rejectsInvalidInput() throws Exception {
		this.mockMvc
			.perform(post("/v1/logins/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginToken\":\"\",\"code\":\"12ab\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("code"))
			.andExpect(jsonPath("$.errors[1].field").value("loginToken"));
	}

	private String startLogin(String loginId) throws Exception {
		String body = startLoginRequest(loginId).andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.loginToken");
	}

	private ResultActions startLoginRequest(String loginId) throws Exception {
		return this.mockMvc.perform(post("/v1/logins").contentType(MediaType.APPLICATION_JSON)
			.content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + PASSWORD + "\"}"));
	}

	private ResultActions verify(String loginToken, OtpCode code) throws Exception {
		return this.mockMvc.perform(post("/v1/logins/verify").contentType(MediaType.APPLICATION_JSON)
			.content("{\"loginToken\":\"" + loginToken + "\",\"code\":\"" + code.value() + "\"}"));
	}

	// 認証アプリが今表示するコード
	private OtpCode validCode() {
		return this.totpVerifier.generate(this.secret, TotpStep.at(this.clock.instant()));
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode wrongCode() {
		TotpStep step = TotpStep.at(this.clock.instant());
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter((code) -> this.totpVerifier.verify(this.secret, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private User putUser(String loginId) {
		UserId userId = UserId.generate();
		User user = User.register(userId, LoginId.of(loginId), this.passwordHasher.hash(new Password(PASSWORD)),
				this.totpSecretEncryptor.encrypt(this.secret, userId), TotpStep.at(this.clock.instant()).plus(-5),
				this.clock.instant());
		items().putUser(user);
		return user;
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.dynamoDbProperties);
	}

}
