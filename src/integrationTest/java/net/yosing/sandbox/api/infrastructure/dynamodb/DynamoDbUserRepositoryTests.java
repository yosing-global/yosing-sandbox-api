package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * ユーザーとログイン ID の項目の読み書き（項目の変換の往復、パスワードの試行の条件付き更新）を、DynamoDB Local で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbUserRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	@Autowired
	DynamoDbUserRepository repository;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * ログイン ID の項目（LOGINID#&lt;loginId&gt; / LOGINID）があれば true、無ければ false を返す。
	 */
	@Test
	void existsByLoginId() {
		items().putLoginId(LoginId.of("dummy_exists"), UserId.generate());

		assertThat(repository.existsByLoginId(LoginId.of("dummy_exists"))).isTrue();
		assertThat(repository.existsByLoginId(LoginId.of("dummy_missing"))).isFalse();
	}

	/**
	 * ログイン ID で読むと、保存したユーザーと同じ値に戻る（ロックの期限・jti があるときも）。無ければ空。
	 */
	@Test
	void findsUserByLoginId() {
		User user = User.restore(UserId.generate(), LoginId.of("dummy_find_user"), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1, 2, 3 }), new TotpStep(123), 5, 2,
				Optional.of(NOW.plus(User.PASSWORD_LOCK_DURATION)), Optional.of("dummy-jti"), NOW);
		items().putUser(user);

		assertThat(repository.findByLoginId(user.loginId())).hasValueSatisfying(found -> {
			assertThat(found.id()).isEqualTo(user.id());
			assertThat(found.passwordHash()).isEqualTo(user.passwordHash());
			assertThat(found.totpSecret()).isEqualTo(user.totpSecret());
			assertThat(found.lastUsedTotpStep()).isEqualTo(user.lastUsedTotpStep());
			assertThat(found.passwordFailureCount()).isEqualTo(5);
			assertThat(found.otpFailureCount()).isEqualTo(2);
			assertThat(found.lockedUntil()).isEqualTo(user.lockedUntil());
			assertThat(found.loginTokenId()).hasValue("dummy-jti");
			assertThat(found.createdAt()).isEqualTo(NOW);
		});
		assertThat(repository.findByLoginId(LoginId.of("dummy_find_missing"))).isEmpty();
	}

	/**
	 * パスワードの試行は、読んだときから変わっていなければ保存できる（5回目はロックの期限も）。
	 * 古い状態を元にした保存は失敗する（同時の試行は1つだけ通る）。
	 */
	@Test
	void savesPasswordAttemptOnlyFromLatestState() {
		User user = newUser("dummy_attempt", 3);
		items().putUser(user);
		User fourth = user.countPasswordAttempt(NOW);
		User fifth = fourth.countPasswordAttempt(NOW);

		assertThat(repository.savePasswordAttempt(user, fourth)).isTrue();
		assertThat(repository.savePasswordAttempt(user, fourth)).isFalse();
		assertThat(repository.savePasswordAttempt(fourth, fifth)).isTrue();

		assertThat(items().user(user.id())).hasValueSatisfying(saved -> {
			assertThat(saved.passwordFailureCount()).isEqualTo(5);
			assertThat(saved.lockedUntil()).hasValue(NOW.plus(User.PASSWORD_LOCK_DURATION));
		});
	}

	/**
	 * ロックの期限が過ぎた後の試行は、ロックの期限を消して保存する。
	 */
	@Test
	void removesExpiredLockOnAttempt() {
		User locked = User.restore(UserId.generate(), LoginId.of("dummy_attempt_unlock"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), 5,
				0, Optional.of(NOW), Optional.empty(), NOW);
		items().putUser(locked);

		assertThat(repository.savePasswordAttempt(locked, locked.countPasswordAttempt(NOW))).isTrue();

		assertThat(items().user(locked.id())).hasValueSatisfying(saved -> {
			assertThat(saved.passwordFailureCount()).isEqualTo(1);
			assertThat(saved.lockedUntil()).isEmpty();
		});
	}

	/**
	 * パスワードが合ったときは、失敗回数を0に戻してロックを解き、jti を保存する。
	 */
	@Test
	void savesPasswordVerified() {
		User user = newUser("dummy_verified", 4);
		items().putUser(user);
		User counted = user.countPasswordAttempt(NOW);
		repository.savePasswordAttempt(user, counted);

		repository.savePasswordVerified(counted.passwordVerified("dummy-new-jti"));

		assertThat(items().user(user.id())).hasValueSatisfying(saved -> {
			assertThat(saved.passwordFailureCount()).isZero();
			assertThat(saved.otpFailureCount()).isZero();
			assertThat(saved.lockedUntil()).isEmpty();
			assertThat(saved.loginTokenId()).hasValue("dummy-new-jti");
		});
	}

	private static User newUser(String loginId, int passwordFailureCount) {
		return User.restore(UserId.generate(), LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), passwordFailureCount, 1, Optional.empty(),
				Optional.empty(), NOW);
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(dynamoDb, properties);
	}

}
