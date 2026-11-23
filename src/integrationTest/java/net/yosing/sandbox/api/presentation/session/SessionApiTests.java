package net.yosing.sandbox.api.presentation.session;

import java.time.Clock;
import java.time.Instant;

import com.jayway.jsonpath.JsonPath;
import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbProperties;
import net.yosing.sandbox.api.infrastructure.dynamodb.DynamoDbTestItems;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * セッションの API（GET /v1/me・DELETE /v1/sessions/current）を、DynamoDB Local で確かめる。
 *
 * <p>ユーザーとセッションは DB に直接作る。テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class SessionApiTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties dynamoDbProperties;

	@Autowired
	Clock clock;

	/**
	 * 有効なセッションなら 200 でログイン ID・登録した日時・延ばした後のセッションの期限を返し、
	 * セッションの最終操作の日時と期限を延ばす（返す期限は、保存した期限と同じ）。
	 */
	@Test
	void returnsCurrentUserAndExtendsSession() throws Exception {
		User user = putUser("dummy_me_ok");
		IssuedSession issued = putSession(user, clock.instant().minusSeconds(3600));

		String body = mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(issued)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.loginId").value("dummy_me_ok"))
			.andExpect(jsonPath("$.createdAt").value(user.createdAt().toString()))
			.andReturn()
			.getResponse()
			.getContentAsString();

		Instant sessionExpiresAt = Instant.parse(JsonPath.read(body, "$.sessionExpiresAt"));
		assertThat(items().session(issued.session().idHash())).hasValueSatisfying(saved -> {
			assertThat(saved.lastAccessedAt()).isAfter(issued.session().lastAccessedAt());
			assertThat(saved.expiresAt()).isAfter(issued.session().expiresAt());
			assertThat(saved.expiresAt()).isEqualTo(sessionExpiresAt);
		});
	}

	/**
	 * Authorization が無い・Bearer でない・セッション ID の形が違う・無いセッションは、401 SESSION_INVALID
	 * （WWW-Authenticate: Bearer を付ける）。
	 */
	@Test
	void rejectsMissingOrInvalidAuthorization() throws Exception {
		mockMvc.perform(get("/v1/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
			.andExpect(jsonPath("$.code").value("SESSION_INVALID"));
		for (String authorization : new String[] { "Basic dummy", "Bearer dummy-session-id",
				"Bearer " + Session.issue(UserId.generate(), clock.instant()).sessionId().value() }) {
			mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, authorization))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("SESSION_INVALID"));
		}
	}

	/**
	 * 期限切れのセッションは 401 SESSION_INVALID。
	 */
	@Test
	void rejectsExpiredSession() throws Exception {
		User user = putUser("dummy_me_expired");
		IssuedSession issued = putSession(user, clock.instant().minus(Session.IDLE_TIMEOUT).minusSeconds(1));

		mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(issued)))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SESSION_INVALID"));
	}

	/**
	 * ログアウトは 204 でセッションを削除し、その後は GET /v1/me が 401。もう一度ログアウトしても 204（大文字小文字の違う bearer でもよい）。
	 */
	@Test
	void logsOut() throws Exception {
		User user = putUser("dummy_logout");
		IssuedSession issued = putSession(user, clock.instant().minusSeconds(60));

		mockMvc.perform(delete("/v1/sessions/current").header(HttpHeaders.AUTHORIZATION, bearer(issued)))
			.andExpect(status().isNoContent());

		assertThat(items().session(issued.session().idHash())).isEmpty();
		mockMvc.perform(get("/v1/me").header(HttpHeaders.AUTHORIZATION, bearer(issued)))
			.andExpect(status().isUnauthorized());
		mockMvc
			.perform(delete("/v1/sessions/current").header(HttpHeaders.AUTHORIZATION,
					"bearer " + issued.sessionId().value()))
			.andExpect(status().isNoContent());
	}

	/**
	 * Authorization が無いログアウトは 401 SESSION_INVALID。
	 */
	@Test
	void rejectsLogoutWithoutAuthorization() throws Exception {
		mockMvc.perform(delete("/v1/sessions/current"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("SESSION_INVALID"));
	}

	private static String bearer(IssuedSession issued) {
		return "Bearer " + issued.sessionId().value();
	}

	private IssuedSession putSession(User user, Instant issuedAt) {
		IssuedSession issued = Session.issue(user.id(), issuedAt);
		items().putSession(issued.session());
		return issued;
	}

	private User putUser(String loginId) {
		User user = User.register(UserId.generate(), LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), Instant.parse("2026-10-01T00:00:00Z"));
		items().putUser(user);
		return user;
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(dynamoDb, dynamoDbProperties);
	}

}
