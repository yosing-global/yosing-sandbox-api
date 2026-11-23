package net.yosing.sandbox.api;

import org.junit.jupiter.api.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 環境名のプロファイルを指定したとき、application.yml のグループで動き方のプロファイルが有効になることを確認する。
 *
 * <p>プロファイルの解決だけを見るため、アプリの Bean は作らない（DynamoDB 等に接続しない）。
 */
class ProfileGroupTests {

	/**
	 * local を指定すると、local と dynamodb-local が有効になる。
	 */
	@Test
	void localActivatesDynamoDbLocal() {
		assertThat(activeProfiles("local")).containsExactlyInAnyOrder("local", "dynamodb-local");
	}

	/**
	 * pr を指定すると、pr・aws・dynamodb-local が有効になる（KMS は本物、DynamoDB はサイドカー）。
	 */
	@Test
	void prActivatesAwsAndDynamoDbLocal() {
		assertThat(activeProfiles("pr")).containsExactlyInAnyOrder("pr", "aws", "dynamodb-local");
	}

	/**
	 * dev を指定すると、dev と aws が有効になる（test / stg / prod も同じ形）。
	 */
	@Test
	void devActivatesAws() {
		assertThat(activeProfiles("dev")).containsExactlyInAnyOrder("dev", "aws");
	}

	private static String[] activeProfiles(String environment) {
		try (ConfigurableApplicationContext context = new SpringApplicationBuilder(EmptyConfiguration.class)
			.web(WebApplicationType.NONE)
			.run("--spring.profiles.active=" + environment)) {
			return context.getEnvironment().getActiveProfiles();
		}
	}

	@Configuration(proxyBeanMethods = false)
	static class EmptyConfiguration {

	}

}
