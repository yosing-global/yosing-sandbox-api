package net.yosing.sandbox.api.presentation.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbProperties;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbTestItems;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * ログインの開始（POST /v1/logins）の API を、DynamoDB Local・bcrypt・ローカルの署名で確かめる。
 *
 * <p>ユーザーは DB に直接作る。テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class StartLoginApiTests {

	private static final String PASSWORD = "dummy correct horse battery";

	private static final String WRONG_PASSWORD = "dummy wrong horse battery";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties dynamoDbProperties;

	@Autowired
	PasswordHasher passwordHasher;

	@Autowired
	LoginTokenSigner loginTokenSigner;

	@Autowired
	Clock clock;

	/**
	 * 正しいパスワードなら 200 で仮認証 JWT と期限を返し、JWT の jti をユーザーに保存する（ログイン ID は大文字でもよい）。
	 */
	@Test
	void issuesLoginToken() throws Exception {
		User user = putUser("dummy_login_ok");

		String body = login("Dummy_Login_OK", PASSWORD).andExpect(status().isOk())
			.andExpect(jsonPath("$.expiresAt").isString())
			.andReturn()
			.getResponse()
			.getContentAsString();

		LoginTokenClaims claims = loginTokenSigner.verify(JsonPath.read(body, "$.loginToken"), clock.instant())
			.orElseThrow();
		assertThat(claims.userId()).isEqualTo(user.id());
		assertThat(items().user(user.id())).hasValueSatisfying(
				saved -> assertThat(saved.loginTokenId()).hasValue(claims.tokenId()));
	}

	/**
	 * パスワードが違えば 401 INVALID_CREDENTIALS で、失敗回数を数える。
	 */
	@Test
	void rejectsWrongPassword() throws Exception {
		User user = putUser("dummy_login_wrong");

		login("dummy_login_wrong", WRONG_PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		assertThat(items().user(user.id())).hasValueSatisfying(
				saved -> assertThat(saved.passwordFailureCount()).isEqualTo(1));
	}

	/**
	 * 5回続けて違えばロックし、その後は正しいパスワードでも同じ 401 INVALID_CREDENTIALS（jti は作らない）。
	 */
	@Test
	void locksAfterFiveFailures() throws Exception {
		User user = putUser("dummy_login_lock");
		for (int attempt = 1; attempt <= User.MAX_PASSWORD_FAILURES; attempt++) {
			login("dummy_login_lock", WRONG_PASSWORD).andExpect(status().isUnauthorized());
		}

		login("dummy_login_lock", PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		assertThat(items().user(user.id())).hasValueSatisfying(saved -> {
			assertThat(saved.lockedUntil()).isPresent();
			assertThat(saved.loginTokenId()).isEmpty();
		});
	}

	/**
	 * 無いログイン ID は、パスワードが違うときと同じ 401 INVALID_CREDENTIALS。
	 */
	@Test
	void rejectsUnknownLoginId() throws Exception {
		login("dummy_login_unknown", PASSWORD).andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
	}

	/**
	 * 形式の違う入力は 400 VALIDATION_FAILED。
	 */
	@Test
	void rejectsInvalidInput() throws Exception {
		login("ab", "dummy").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value("loginId"))
			.andExpect(jsonPath("$.errors[1].field").value("password"));
	}

	private ResultActions login(String loginId, String password) throws Exception {
		return mockMvc.perform(post("/v1/logins").contentType(MediaType.APPLICATION_JSON)
			.content("{\"loginId\":\"" + loginId + "\",\"password\":\"" + password + "\"}"));
	}

	private User putUser(String loginId) {
		User user = User.register(UserId.generate(), LoginId.of(loginId), passwordHasher.hash(new Password(PASSWORD)),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), clock.instant());
		items().putUser(user);
		return user;
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(dynamoDb, dynamoDbProperties);
	}

}
