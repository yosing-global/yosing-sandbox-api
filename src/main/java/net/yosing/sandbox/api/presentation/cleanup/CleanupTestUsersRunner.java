package net.yosing.sandbox.api.presentation.cleanup;

import net.yosing.sandbox.api.application.cleanup.CleanupTestUsersResult;
import net.yosing.sandbox.api.application.cleanup.CleanupTestUsersUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * テストのユーザーの掃除の入口（cleanup のプロファイルのときだけ動く）。
 *
 * <p>api の CI が、api のタスク定義を {@code SPRING_PROFILES_ACTIVE=<環境名>,cleanup} に差し替えて、ECS のタスクとして一度だけ動かす
 * （.github/workflows/_cleanup.yml）。Web のサーバーは起動せず（application-cleanup.yml）、終わったらアプリを終える（ApiApplication）。
 * 失敗したら例外で終わり、タスクの終了コードが 0 以外になる。
 *
 * <p>ログには件数だけを出す（ログイン ID 等の中身は出さない）。
 */
@Component
@Profile("cleanup")
class CleanupTestUsersRunner implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(CleanupTestUsersRunner.class);

	private final CleanupTestUsersUseCase useCase;

	private final String environment;

	CleanupTestUsersRunner(CleanupTestUsersUseCase useCase,
			@Value("${sandbox.environment.role-env}") String environment) {
		this.useCase = useCase;
		this.environment = environment;
	}

	@Override
	public void run(ApplicationArguments args) {
		LOGGER.info("テストのユーザーの掃除を始める（環境：{}）", this.environment);
		CleanupTestUsersResult result = this.useCase.cleanup(this.environment);
		LOGGER.info("テストのユーザーの掃除が終わった（見つかった：{}、期間を過ぎた：{}、消した：{}）", result.found(), result.expired(), result.deleted());
	}

}
