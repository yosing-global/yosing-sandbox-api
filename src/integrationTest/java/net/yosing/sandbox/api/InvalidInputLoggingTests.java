package net.yosing.sandbox.api;

import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入力の誤り（400）のとき、入力した値（形式の違うパスワード・コード）がログに出ないことを、本番と同じログの詳しさで確かめる。
 *
 * <p>入力の誤りの例外のメッセージには、入力した値（rejected value）が入る。Spring の設定（例：解決した例外を WARN で出す
 * {@code spring.mvc.log-resolved-exception}）や、アプリのログの足し方によっては、それがログに出てしまうため、その変更に気づけるようにする。
 * Spring の Web の部分を DEBUG にすると、この値が出ることがあるため、AWS の環境では DEBUG にしない。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
// ログの詳しさは JVM 全体で1つのため、先に動いた DEBUG のテスト（SensitiveDataLoggingTests）の設定が残らないよう、
// 本番と同じ INFO を明示し、このクラス用のコンテキストを作る（コンテキストを作るときに、ログの設定が読み直される）
@TestPropertySource(
		properties = { "logging.level.net.yosing.sandbox.api=INFO", "logging.level.org.springframework.web=INFO" })
@ExtendWith(OutputCaptureExtension.class)
class InvalidInputLoggingTests {

	// 形式の違う値（パスワードは12文字未満、コードは数字6桁でない）。本物の入力の打ち間違いを想定する
	private static final String SHORT_PASSWORD = "dummy-pw";

	private static final String BAD_CODE = "12ab56";

	@Autowired
	MockMvc mockMvc;

	/**
	 * 登録・ログインで形式の違う値を送ると 400 になり、その値はログに出ない。
	 */
	@Test
	void doesNotLogRejectedValues(CapturedOutput output) throws Exception {
		postExpectingBadRequest("/v1/registrations",
				"{\"loginId\":\"dummy_log_invalid\",\"password\":\"" + SHORT_PASSWORD + "\"}");
		postExpectingBadRequest("/v1/logins",
				"{\"loginId\":\"dummy_log_invalid\",\"password\":\"" + SHORT_PASSWORD + "\"}");
		postExpectingBadRequest("/v1/registrations/" + RegistrationId.generate() + "/verify",
				"{\"code\":\"" + BAD_CODE + "\"}");
		postExpectingBadRequest("/v1/logins/verify",
				"{\"loginToken\":\"dummy.login.token\",\"code\":\"" + BAD_CODE + "\"}");

		assertThat(output.getAll()).doesNotContain(SHORT_PASSWORD, BAD_CODE);
	}

	private void postExpectingBadRequest(String path, String json) throws Exception {
		this.mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
			.andExpect(status().isBadRequest());
	}

}
