package net.yosing.sandbox.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * 環境名のプロファイルを指定したとき、application.yml のグループで動き方のプロファイルが有効になることを確認する。
 *
 * <p>プロファイルの解決だけを見るため、アプリの Bean は作らない（DynamoDB 等に接続しない）。
 */
class ProfileGroupTests {

	@Test
	void localActivatesDynamoDbLocal() {
		assertThat(activeProfiles("local")).containsExactlyInAnyOrder("local", "dynamodb-local");
	}

	@Test
	void prActivatesAwsAndDynamoDbLocal() {
		assertThat(activeProfiles("pr")).containsExactlyInAnyOrder("pr", "aws", "dynamodb-local");
	}

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
