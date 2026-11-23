package net.yosing.sandbox.api.presentation.registration;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
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
 * 登録の確定（POST /v1/registrations/{registrationId}/verify）の API を、DynamoDB Local とローカルの暗号化で確かめる。
 *
 * <p>仮登録は登録開始の API で作り、正しいコードは DB の秘密情報を復号して計算する（認証アプリの代わり）。
 * テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class VerifyRegistrationApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties dynamoDbProperties;

	@Autowired
	TotpSecretEncryptor totpSecretEncryptor;

	@Autowired
	TotpVerifier totpVerifier;

	@Autowired
	Clock clock;

	/**
	 * 正しいコードなら 201 でセッション ID と期限を返し、ユーザー・ログイン ID・セッション（ID のハッシュがキー）を作って、仮登録を削除する。
	 */
	@Test
	void completesRegistration() throws Exception {
		Registration registration = startRegistration("dummy_verify_ok");

		String body = verify(registration.id().toString(), validCode(registration)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.sessionExpiresAt").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		SessionId sessionId = new SessionId(JsonPath.read(body, "$.sessionId"));
		assertThat(items().userItem(registration.userId())).isPresent();
		assertThat(items().loginIdItem(registration.loginId())).isPresent();
		assertThat(items().sessionItem(sessionId.hash())).isPresent();
		assertThat(items().registration(registration.id())).isEmpty();
	}

	/**
	 * 確定した後に同じ登録 ID を使うと 404 REGISTRATION_NOT_FOUND（二重の確定はできない）。
	 */
	@Test
	void rejectsCompletedRegistration() throws Exception {
		Registration registration = startRegistration("dummy_verify_twice");
		verify(registration.id().toString(), validCode(registration)).andExpect(status().isCreated());

		verify(registration.id().toString(), validCode(registration)).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
	}

	/**
	 * コードが違えば 401 OTP_INVALID で、残りの回数を返す。
	 */
	@Test
	void rejectsWrongCode() throws Exception {
		Registration registration = startRegistration("dummy_verify_wrong");

		verify(registration.id().toString(), wrongCode(registration)).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("OTP_INVALID"))
			.andExpect(jsonPath("$.remainingAttempts").value(4));
	}

	/**
	 * 5回続けて間違えると 429 OTP_ATTEMPTS_EXCEEDED で仮登録を削除し、その後は正しいコードでも 404。
	 */
	@Test
	void deletesRegistrationAfterFiveFailures() throws Exception {
		Registration registration = startRegistration("dummy_verify_limit");
		String id = registration.id().toString();
		for (int attempt = 1; attempt <= 4; attempt++) {
			verify(id, wrongCode(registration)).andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.remainingAttempts").value(5 - attempt));
		}

		verify(id, wrongCode(registration)).andExpect(status().isTooManyRequests())
			.andExpect(jsonPath("$.code").value("OTP_ATTEMPTS_EXCEEDED"));
		assertThat(items().registration(registration.id())).isEmpty();
		verify(id, validCode(registration)).andExpect(status().isNotFound());
	}

	/**
	 * 無い登録 ID・UUID の形でない登録 ID は 404 REGISTRATION_NOT_FOUND。
	 */
	@Test
	void rejectsUnknownRegistrationId() throws Exception {
		verify(RegistrationId.generate().toString(), new OtpCode("123456")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
		verify("dummy-not-a-uuid", new OtpCode("123456")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("REGISTRATION_NOT_FOUND"));
	}

	/**
	 * 数字6桁でないコードは 400 VALIDATION_FAILED。
	 */
	@Test
	void rejectsInvalidCodeFormat() throws Exception {
		this.mockMvc
			.perform(post("/v1/registrations/" + RegistrationId.generate() + "/verify")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"12ab\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("code"));
	}

	/**
	 * 仮登録の後に同じログイン ID が本登録されていたら 409 LOGIN_ID_TAKEN で、仮登録は残す。
	 */
	@Test
	void rejectsLoginIdTakenMeanwhile() throws Exception {
		Registration registration = startRegistration("dummy_verify_taken");
		items().putLoginId(registration.loginId(), UserId.generate());

		verify(registration.id().toString(), validCode(registration)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));
		assertThat(items().userItem(registration.userId())).isEmpty();
		assertThat(items().registration(registration.id())).isPresent();
	}

	private Registration startRegistration(String loginId) throws Exception {
		String body = this.mockMvc
			.perform(post("/v1/registrations").contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"" + loginId + "\",\"password\":\"dummy correct horse battery\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return items().registration(RegistrationId.parse(JsonPath.read(body, "$.registrationId"))).orElseThrow();
	}

	private ResultActions verify(String registrationId, OtpCode code) throws Exception {
		return this.mockMvc
			.perform(post("/v1/registrations/" + registrationId + "/verify").contentType(MediaType.APPLICATION_JSON)
				.content("{\"code\":\"" + code.value() + "\"}"));
	}

	// 認証アプリが今表示するコード
	private OtpCode validCode(Registration registration) {
		return this.totpVerifier.generate(secret(registration), TotpStep.at(this.clock.instant()));
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode wrongCode(Registration registration) {
		TotpSecret secret = secret(registration);
		TotpStep step = TotpStep.at(this.clock.instant());
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter((code) -> this.totpVerifier.verify(secret, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private TotpSecret secret(Registration registration) {
		return this.totpSecretEncryptor.decrypt(registration.totpSecret(), registration.userId());
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.dynamoDbProperties);
	}

}
