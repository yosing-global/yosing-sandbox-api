package net.yosing.sandbox.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * 環境名のプロファイルを指定したとき、application.yml のグループで動き方のプロファイルが有効になることを確認する。
 */
class ProfileGroupTests {

	@Nested
	@SpringBootTest(properties = "spring.profiles.active=local")
	class Local {

		@Autowired
		Environment environment;

		@Test
		void activatesDynamoDbLocalOnly() {
			assertThat(environment.getActiveProfiles()).containsExactlyInAnyOrder("local", "dynamodb-local");
		}

	}

	@Nested
	@SpringBootTest(properties = "spring.profiles.active=pr")
	class Pr {

		@Autowired
		Environment environment;

		@Test
		void activatesAwsAndDynamoDbLocal() {
			assertThat(environment.getActiveProfiles()).containsExactlyInAnyOrder("pr", "aws", "dynamodb-local");
		}

	}

	@Nested
	@SpringBootTest(properties = "spring.profiles.active=dev")
	class Dev {

		@Autowired
		Environment environment;

		@Test
		void activatesAws() {
			assertThat(environment.getActiveProfiles()).containsExactlyInAnyOrder("dev", "aws");
		}

	}

}
