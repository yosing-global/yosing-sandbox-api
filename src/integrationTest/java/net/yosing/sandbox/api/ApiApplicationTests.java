package net.yosing.sandbox.api;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * local のプロファイルで、アプリが起動できる（Bean の組み立てと設定の読み込みに失敗しない）ことを確かめる。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class ApiApplicationTests {

	/**
	 * アプリの起動に成功する。
	 */
	@Test
	void contextLoads() {
	}

}
