package net.yosing.sandbox.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class ApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
