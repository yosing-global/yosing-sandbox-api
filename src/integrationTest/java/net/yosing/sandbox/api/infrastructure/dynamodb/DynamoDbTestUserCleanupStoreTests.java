package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.TestUserCleanupStore.TestUser;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * テストのユーザーの掃除の、探す（Scan）・消す（ユーザーとログイン ID の項目をまとめて、条件付きで）を、DynamoDB Local で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID の先頭を使い、その先頭で探す。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbTestUserCleanupStoreTests {

	private static final Instant CREATED_AT = Instant.parse("2026-11-01T00:00:00Z");

	@Autowired
	DynamoDbTestUserCleanupStore store;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * ログイン ID が先頭に合うユーザーだけを、ID・ログイン ID・登録した日時と一緒に返す（ほかのユーザーは返さない）。
	 */
	@Test
	void findsOnlyUsersWithPrefix() {
		User first = putUser("e2e_dummy_cleanfind_a");
		User second = putUser("e2e_dummy_cleanfind_b");
		putUser("dummy_cleanfind_c");

		assertThat(this.store.findByLoginIdPrefix("e2e_dummy_cleanfind_")).containsExactlyInAnyOrder(testUser(first),
				testUser(second));
	}

	/**
	 * ユーザーの項目とログイン ID の項目を、まとめて消す。
	 */
	@Test
	void deletesUserAndLoginIdItems() {
		User user = putUser("e2e_dummy_cleandel_a");

		assertThat(this.store.delete(testUser(user))).isTrue();

		assertThat(items().userItem(user.id())).isEmpty();
		assertThat(items().loginIdItem(user.loginId())).isEmpty();
	}

	/**
	 * 既に消えていたら、false を返す。
	 */
	@Test
	void returnsFalseWhenAlreadyDeleted() {
		User user = putUser("e2e_dummy_cleangone_a");
		this.store.delete(testUser(user));

		assertThat(this.store.delete(testUser(user))).isFalse();
	}

	/**
	 * ログイン ID の項目がほかのユーザーを指していたら、どちらの項目も消さずに false を返す。
	 */
	@Test
	void keepsItemsWhenLoginIdPointsToAnotherUser() {
		User user = putUser("e2e_dummy_cleanmis_a");
		items().putLoginId(user.loginId(), UserId.generate());

		assertThat(this.store.delete(testUser(user))).isFalse();

		assertThat(items().userItem(user.id())).isPresent();
		assertThat(items().loginIdItem(user.loginId())).isPresent();
	}

	/**
	 * テストのユーザーのログイン ID（e2e_dummy_ で始まる）でなければ、探さず・消さずに失敗する。
	 */
	@Test
	void rejectsNonTestLoginId() {
		User user = putUser("dummy_cleanreject_a");

		assertThatThrownBy(() -> this.store.findByLoginIdPrefix("dummy_")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.store.delete(testUser(user))).isInstanceOf(IllegalArgumentException.class);

		assertThat(items().userItem(user.id())).isPresent();
	}

	private User putUser(String loginId) {
		User user = User.register(UserId.generate(), LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), CREATED_AT);
		items().putUser(user);
		return user;
	}

	private static TestUser testUser(User user) {
		return new TestUser(user.id(), user.loginId(), user.createdAt());
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.properties);
	}

}
