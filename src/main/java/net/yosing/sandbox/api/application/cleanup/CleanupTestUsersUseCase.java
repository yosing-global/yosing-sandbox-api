package net.yosing.sandbox.api.application.cleanup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import net.yosing.sandbox.api.application.port.TestUserCleanupStore;
import net.yosing.sandbox.api.application.port.TestUserCleanupStore.TestUser;

import org.springframework.stereotype.Service;

/**
 * E2E が作ったテストのユーザーの掃除（Notion の E2E の設計の 11章）。
 *
 * <p>ログイン ID が {@code e2e_dummy_} で始まり、登録してから {@link #RETENTION} を過ぎたユーザーを消す。
 * 期間で絞るため、実行中の E2E のユーザーは消さない。セッション・仮登録は TTL で消えるため、消さない
 * （セッションは最長12時間、仮登録は10分で、残す期間より短い）。
 *
 * <p>api の CI が、api を掃除のモード（cleanup のプロファイル）で一度だけ動かして呼ぶ（presentation.cleanup）。
 * 消してよい環境は {@link #ALLOWED_ENVIRONMENTS} だけで、ほかの環境（prod 等）では何もしないで失敗する。
 */
@Service
public class CleanupTestUsersUseCase {

	/**
	 * 登録してから、消すまでに残す期間。
	 */
	public static final Duration RETENTION = Duration.ofHours(24);

	/**
	 * 掃除してよい環境（ROLE_ENV）。E2E を流す環境（dev・test・stg）だけ。prod は足さない。
	 */
	public static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("dev", "test", "stg");

	private final TestUserCleanupStore store;

	private final Clock clock;

	public CleanupTestUsersUseCase(TestUserCleanupStore store, Clock clock) {
		this.store = store;
		this.clock = clock;
	}

	/**
	 * テストのユーザーを掃除する。
	 *
	 * @param environment 動いている環境（ROLE_ENV）
	 * @return 件数
	 * @throws IllegalStateException 掃除してよい環境でない場合（何も探さず、消さない）
	 */
	public CleanupTestUsersResult cleanup(String environment) {
		if (!ALLOWED_ENVIRONMENTS.contains(environment)) {
			throw new IllegalStateException("テストのユーザーの掃除は、この環境では行わない：" + environment);
		}
		// ちょうど残す期間の境目のユーザーは残す（境目より前に登録したユーザーだけを消す）
		Instant cutoff = clock.instant().minus(RETENTION);
		List<TestUser> found = store.findByLoginIdPrefix(TestUserCleanupStore.TEST_LOGIN_ID_PREFIX);
		int expired = 0;
		int deleted = 0;
		for (TestUser user : found) {
			if (!user.createdAt().isBefore(cutoff)) {
				continue;
			}
			expired++;
			if (store.delete(user)) {
				deleted++;
			}
		}
		return new CleanupTestUsersResult(found.size(), expired, deleted);
	}

}
