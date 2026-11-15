package net.yosing.sandbox.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 認証アプリに表示されるサービスの名前（sandbox.otpauth.issuer）が、環境ごとに決まった値になることを確認する。
 *
 * <p>設定ファイルの解決だけを見るため、アプリの Bean は作らない（DynamoDB 等に接続しない）。
 */
class OtpauthIssuerPropertyTests {

	/**
	 * prod 以外では、アプリの環境の名前を付ける（同じ端末に複数の環境を登録しても見分けられるように）。
	 */
	@Test
	void addsAppEnvOutsideProd() {
		assertThat(issuer("dev", "dummy-pr12")).isEqualTo("sandbox-dummy-pr12");
	}

	/**
	 * prod では、環境の名前を付けない（利用者に見せる名前のため）。
	 */
	@Test
	void usesProductNameInProd() {
		assertThat(issuer("prod", "dummy-prod")).isEqualTo("sandbox");
	}

	private static String issuer(String environment, String appEnv) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfiguration.class)
			.web(WebApplicationType.NONE)
			.run("--spring.profiles.active=" + environment, "--sandbox.environment.app-env=" + appEnv)) {
			return context.getEnvironment().getProperty("sandbox.otpauth.issuer");
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyConfiguration {

	}

}
