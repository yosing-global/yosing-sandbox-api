package net.yosing.sandbox.api.presentation.registration;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登録の開始（POST /v1/registrations）の API を、DynamoDB Local とローカルの暗号化で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class StartRegistrationApiTests {

	private static final String PATH = "/v1/registrations";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties dynamoDbProperties;

	/**
	 * 201 で登録 ID・設定 URI・期限を返し、仮登録を保存する（ログイン ID は小文字、パスワードは bcrypt のハッシュ、
	 * 秘密情報は暗号化）。
	 */
	@Test
	void startsRegistration() throws Exception {
		String body = this.mockMvc
			.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"Dummy_Start_OK\",\"password\":\"dummy correct horse battery\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.otpauthUri", startsWith("otpauth://totp/sandbox-local:dummy_start_ok?secret=")))
			.andExpect(jsonPath("$.expiresAt").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String registrationId = JsonPath.read(body, "$.registrationId");
		assertThat(items().registration(RegistrationId.parse(registrationId))).hasValueSatisfying((saved) -> {
			assertThat(saved.loginId()).isEqualTo(LoginId.of("dummy_start_ok"));
			assertThat(saved.passwordHash().value()).startsWith("{bcrypt}$2a$12$");
			assertThat(saved.totpSecret().bytes()).isNotEmpty();
			assertThat(saved.otpFailureCount()).isZero();
		});
	}

	/**
	 * 形式の違う入力は 400 VALIDATION_FAILED で、項目ごとの誤りを返す（入力の値は返さない）。
	 */
	@Test
	void rejectsInvalidInput() throws Exception {
		this.mockMvc
			.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"ab\",\"password\":\"dummy\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("loginId"))
			.andExpect(jsonPath("$.errors[1].field").value("password"))
			.andExpect(jsonPath("$.errors[1].rejectedValue").doesNotExist());
	}

	/**
	 * 項目が無いときも 400 VALIDATION_FAILED で、無い項目を返す。
	 */
	@Test
	void rejectsMissingFields() throws Exception {
		this.mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("loginId"))
			.andExpect(jsonPath("$.errors[1].field").value("password"));
	}

	/**
	 * JSON の形式の誤りも 400 VALIDATION_FAILED（項目ごとの誤りは空）。
	 */
	@Test
	void rejectsMalformedJson() throws Exception {
		this.mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors", empty()));
	}

	/**
	 * 本登録済みのログイン ID（大文字で入力しても同じ）は 409 LOGIN_ID_TAKEN。
	 */
	@Test
	void rejectsTakenLoginId() throws Exception {
		items().putLoginId(LoginId.of("dummy_taken_id"), UserId.generate());

		this.mockMvc
			.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
				.content("{\"loginId\":\"Dummy_Taken_ID\",\"password\":\"dummy correct horse battery\"}"))
			.andExpect(status().isConflict())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(409))
			.andExpect(jsonPath("$.code").value("LOGIN_ID_TAKEN"));
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.dynamoDbProperties);
	}

}
