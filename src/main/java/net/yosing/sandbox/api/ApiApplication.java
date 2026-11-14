package net.yosing.sandbox.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = SpringApplication.run(ApiApplication.class, args);
		// テストのユーザーの掃除（cleanup のプロファイル）は、一度動いたら終える（ECS のタスクを止めるため）。
		//   掃除が失敗したときは、run が例外を投げて、ここまで来ずに終わる（終了コードは 0 以外）
		if (context.getEnvironment().matchesProfiles("cleanup")) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
