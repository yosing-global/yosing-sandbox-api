package net.yosing.sandbox.api.application.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import net.yosing.sandbox.api.application.port.FakeTestUserCleanupStore;
import net.yosing.sandbox.api.application.port.TestUserCleanupStore.TestUser;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * テストのユーザーの掃除（残す期間の境目、件数、掃除してよい環境）を確かめる。
 */
class CleanupTestUsersUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-13T00:00:00Z");

	private final FakeTestUserCleanupStore store = new FakeTestUserCleanupStore();

	private final CleanupTestUsersUseCase useCase = new CleanupTestUsersUseCase(store,
			Clock.fixed(NOW, ZoneOffset.UTC));

	/**
	 * 登録してから24時間を過ぎたユーザーだけを消す（ちょうど24時間前のユーザーは残す）。
	 */
	@Test
	void deletesOnlyUsersOlderThanRetention() {
		TestUser old = testUser("e2e_dummy_old", NOW.minusSeconds(24 * 3600 + 1));
		TestUser boundary = testUser("e2e_dummy_boundary", NOW.minusSeconds(24 * 3600));
		TestUser recent = testUser("e2e_dummy_recent", NOW.minusSeconds(60));
		store.add(old);
		store.add(boundary);
		store.add(recent);

		CleanupTestUsersResult result = useCase.cleanup("test");

		assertThat(result).isEqualTo(new CleanupTestUsersResult(3, 1, 1));
		assertThat(store.users()).containsExactlyInAnyOrder(boundary, recent);
	}

	/**
	 * 消すときに条件に合わなかったユーザー（既に消えた等）は、消した数に数えない。
	 */
	@Test
	void doesNotCountRejectedDeletes() {
		TestUser deleted = testUser("e2e_dummy_deleted", NOW.minusSeconds(48 * 3600));
		TestUser rejected = testUser("e2e_dummy_rejected", NOW.minusSeconds(48 * 3600));
		store.add(deleted);
		store.add(rejected);
		store.rejectDelete(rejected.id());

		CleanupTestUsersResult result = useCase.cleanup("dev");

		assertThat(result).isEqualTo(new CleanupTestUsersResult(2, 2, 1));
	}

	/**
	 * 掃除してよい環境（dev・test・stg）では、テストのユーザーがいなければ、件数はすべて0。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "dev", "test", "stg" })
	void returnsZeroWhenNoTestUsers(String environment) {
		assertThat(useCase.cleanup(environment)).isEqualTo(new CleanupTestUsersResult(0, 0, 0));
	}

	/**
	 * 掃除してよい環境（dev・test・stg）でなければ、探さずに失敗する（prod 等で消さない）。
	 */
	@ParameterizedTest
	@ValueSource(strings = { "prod", "pr", "local", "dummy-env" })
	void rejectsOtherEnvironments(String environment) {
		store.add(testUser("e2e_dummy_old", NOW.minusSeconds(48 * 3600)));

		assertThatThrownBy(() -> useCase.cleanup(environment)).isInstanceOf(IllegalStateException.class);

		assertThat(store.findCount()).isZero();
		assertThat(store.users()).hasSize(1);
	}

	private static TestUser testUser(String loginId, Instant createdAt) {
		return new TestUser(UserId.generate(), new LoginId(loginId), createdAt);
	}

}
