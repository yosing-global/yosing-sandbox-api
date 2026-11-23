package net.yosing.sandbox.api;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
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
import org.junit.jupiter.api.extension.ExtendWith;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登録・ログイン・セッションの API を通しても、ログに秘密の値（パスワード・OTP コード・TOTP の秘密情報・設定 URI・
 * セッション ID・仮認証 JWT・パスワードのハッシュ）が出ないことを確かめる。
 *
 * <p>ふつうの詳しさ（INFO）ではアプリがほとんどログを出さないため、アプリと Spring の Web の部分を DEBUG にして動かす
 * （Spring が要求・応答の型を toString でログに出すため、toString で隠しているかも確かめられる）。
 * ログは標準出力を捕まえて確かめる（{@link OutputCaptureExtension}）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
@TestPropertySource(
		properties = { "logging.level.net.yosing.sandbox.api=DEBUG", "logging.level.org.springframework.web=DEBUG" })
@ExtendWith(OutputCaptureExtension.class)
class SensitiveDataLoggingTests {

	private static final String PASSWORD = "dummy-log-check-password-0001";

	private static final String WRONG_PASSWORD = "dummy-log-check-wrong-0002";

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

	/**
	 * 登録の開始・確定（コード違いを含む）、ログイン中のユーザーの情報、ログアウトを通しても、秘密の値がログに出ない
	 * （要求の型はログに出るが、パスワードは隠れている）。
	 */
	@Test
	void doesNotLogSecretsInRegistrationAndSession(CapturedOutput output) throws Exception {
		String startBody = postJson("/v1/registrations",
				"{\"loginId\":\"dummy_log_reg\",\"password\":\"" + PASSWORD + "\"}")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String otpauthUri = JsonPath.read(startBody, "$.otpauthUri");
		Registration registration = items()
			.registration(RegistrationId.parse(JsonPath.read(startBody, "$.registrationId")))
			.orElseThrow();
		TotpSecret secret = this.totpSecretEncryptor.decrypt(registration.totpSecret(), registration.userId());
		OtpCode wrongCode = wrongCode(secret);
		OtpCode validCode = this.totpVerifier.generate(secret, TotpStep.at(this.clock.instant()));

		String verifyPath = "/v1/registrations/" + registration.id() + "/verify";
		postJson(verifyPath, "{\"code\":\"" + wrongCode.value() + "\"}").andExpect(status().isUnauthorized());
		String sessionId = JsonPath
			.read(postJson(verifyPath, "{\"code\":\"" + validCode.value() + "\"}").andExpect(status().isCreated())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.sessionId");
		this.mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionId))
			.andExpect(status().isOk());
		this.mockMvc.perform(delete("/v1/sessions/current").header(HttpHeaders.AUTHORIZATION, "Bearer " + sessionId))
			.andExpect(status().isNoContent());

		// ログを捕まえられていて、要求の型が隠した形で出ていること（下の確認が、空のログに対するものでないこと）
		assertThat(output.getAll()).contains("password=****");
		assertNotLogged(output, PASSWORD, otpauthUri, otpauthUri.replaceAll(".*secret=([A-Z2-7]+).*", "$1"), sessionId,
				registration.passwordHash().value());
		assertCodeNotLogged(output, wrongCode, validCode);
	}

	/**
	 * ログインの開始（パスワード違いを含む）・確定（コード違いを含む）を通しても、秘密の値がログに出ない。
	 */
	@Test
	void doesNotLogSecretsInLogin(CapturedOutput output) throws Exception {
		TotpSecret secret = TotpSecret.generate();
		UserId userId = UserId.generate();
		User user = User.register(userId, LoginId.of("dummy_log_login"),
				this.passwordHasher.hash(new Password(PASSWORD)), this.totpSecretEncryptor.encrypt(secret, userId),
				TotpStep.at(this.clock.instant()).plus(-5), this.clock.instant());
		items().putUser(user);
		OtpCode wrongCode = wrongCode(secret);
		OtpCode validCode = this.totpVerifier.generate(secret, TotpStep.at(this.clock.instant()));

		postJson("/v1/logins", "{\"loginId\":\"dummy_log_login\",\"password\":\"" + WRONG_PASSWORD + "\"}")
			.andExpect(status().isUnauthorized());
		String loginToken = JsonPath
			.read(postJson("/v1/logins", "{\"loginId\":\"dummy_log_login\",\"password\":\"" + PASSWORD + "\"}")
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString(), "$.loginToken");
		postJson("/v1/logins/verify", "{\"loginToken\":\"" + loginToken + "\",\"code\":\"" + wrongCode.value() + "\"}")
			.andExpect(status().isUnauthorized());
		String sessionId = JsonPath.read(postJson("/v1/logins/verify",
				"{\"loginToken\":\"" + loginToken + "\",\"code\":\"" + validCode.value() + "\"}")
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.sessionId");

		assertThat(output.getAll()).contains("password=****");
		assertNotLogged(output, PASSWORD, WRONG_PASSWORD, loginToken, sessionId, user.passwordHash().value());
		assertCodeNotLogged(output, wrongCode, validCode);
	}

	private static void assertNotLogged(CapturedOutput output, String... values) {
		for (String value : values) {
			assertThat(output.getAll()).doesNotContain(value);
		}
	}

	// コードは数字6桁のため、日時やポート番号等と偶然一致しないよう、項目名と一緒に出ていないことを確かめる
	private static void assertCodeNotLogged(CapturedOutput output, OtpCode... codes) {
		for (OtpCode code : codes) {
			assertThat(output.getAll()).doesNotContain("\"code\":\"" + code.value(), "code=" + code.value());
		}
	}

	private ResultActions postJson(String path, String json) throws Exception {
		return this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode wrongCode(TotpSecret secret) {
		TotpStep step = TotpStep.at(this.clock.instant());
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter((code) -> this.totpVerifier.verify(secret, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.dynamoDbProperties);
	}

}
