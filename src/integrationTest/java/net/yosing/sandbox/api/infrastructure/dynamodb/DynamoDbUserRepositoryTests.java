package net.yosing.sandbox.api.infrastructure.dynamodb;

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
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ユーザーとログイン ID の項目の読み書き（項目の変換の往復、パスワード・OTP の試行の条件付き更新、OTP のロック）を、
 * DynamoDB Local で確かめる。
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

		assertThat(this.repository.existsByLoginId(LoginId.of("dummy_exists"))).isTrue();
		assertThat(this.repository.existsByLoginId(LoginId.of("dummy_missing"))).isFalse();
	}

	/**
	 * ログイン ID で読むと、保存したユーザーと同じ値に戻る（ロックの期限・jti があるときも）。無ければ空。
	 */
	@Test
	void findsUserByLoginId() {
		User user = User.restore(UserId.generate(), LoginId.of("dummy_find_user"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1, 2, 3 }),
				new TotpStep(123), 5, 2, Optional.of(NOW.plus(User.LOCK_DURATION)), Optional.of("dummy-jti"), NOW);
		items().putUser(user);

		assertThat(this.repository.findByLoginId(user.loginId())).hasValueSatisfying((found) -> {
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
		assertThat(this.repository.findByLoginId(LoginId.of("dummy_find_missing"))).isEmpty();
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

		assertThat(this.repository.savePasswordAttempt(user, fourth)).isTrue();
		assertThat(this.repository.savePasswordAttempt(user, fourth)).isFalse();
		assertThat(this.repository.savePasswordAttempt(fourth, fifth)).isTrue();

		assertThat(items().user(user.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.passwordFailureCount()).isEqualTo(5);
			assertThat(saved.lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
		});
	}

	/**
	 * ロックの期限が過ぎた後の試行は、ロックの期限を消し、OTP の失敗回数も0に戻して保存する。
	 */
	@Test
	void removesExpiredLockOnAttempt() {
		User locked = User.restore(UserId.generate(), LoginId.of("dummy_attempt_unlock"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), 2,
				User.MAX_OTP_FAILURES, Optional.of(NOW), Optional.empty(), NOW);
		items().putUser(locked);

		assertThat(this.repository.savePasswordAttempt(locked, locked.countPasswordAttempt(NOW))).isTrue();

		assertThat(items().user(locked.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.passwordFailureCount()).isEqualTo(1);
			assertThat(saved.otpFailureCount()).isZero();
			assertThat(saved.lockedUntil()).isEmpty();
		});
	}

	/**
	 * OTP の失敗回数が読んだときから変わっていたら、パスワードの試行は保存できない。
	 */
	@Test
	void rejectsPasswordAttemptWhenOtpCountChanged() {
		User user = newUser("dummy_attempt_otp_changed", 0).passwordVerified("dummy-jti");
		items().putUser(user);
		this.repository.countOtpAttempt(user.id(), "dummy-jti");

		assertThat(this.repository.savePasswordAttempt(user, user.countPasswordAttempt(NOW))).isFalse();
	}

	/**
	 * パスワードが合ったときは、パスワードの失敗回数を0に戻してロックを解き、jti を保存する（OTP の失敗回数は変えない）。
	 */
	@Test
	void savesPasswordVerified() {
		User user = User.restore(UserId.generate(), LoginId.of("dummy_verified"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), 4,
				2, Optional.empty(), Optional.empty(), NOW);
		items().putUser(user);
		User counted = user.countPasswordAttempt(NOW);
		this.repository.savePasswordAttempt(user, counted);

		this.repository.savePasswordVerified(counted.passwordVerified("dummy-new-jti"));

		assertThat(items().user(user.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.passwordFailureCount()).isZero();
			assertThat(saved.otpFailureCount()).isEqualTo(2);
			assertThat(saved.lockedUntil()).isEmpty();
			assertThat(saved.loginTokenId()).hasValue("dummy-new-jti");
		});
	}

	/**
	 * ID で読むと保存したユーザーを返し、無ければ空。
	 */
	@Test
	void findsUserById() {
		User user = newUser("dummy_find_by_id", 0);
		items().putUser(user);

		assertThat(this.repository.findById(user.id()))
			.hasValueSatisfying((found) -> assertThat(found.loginId()).isEqualTo(user.loginId()));
		assertThat(this.repository.findById(UserId.generate())).isEmpty();
	}

	/**
	 * OTP の試行は、今の仮認証の jti のときだけ1ずつ数え、上限（5回）に達したら数えない。
	 */
	@Test
	void countsOtpAttemptForCurrentLoginToken() {
		User user = newUser("dummy_otp_attempt", 0).passwordVerified("dummy-jti");
		items().putUser(user);

		assertThat(this.repository.countOtpAttempt(user.id(), "dummy-other-jti")).isEmpty();
		for (int attempt = 1; attempt <= User.MAX_OTP_FAILURES; attempt++) {
			int expected = attempt;
			assertThat(this.repository.countOtpAttempt(user.id(), "dummy-jti"))
				.hasValueSatisfying((counted) -> assertThat(counted.otpFailureCount()).isEqualTo(expected));
		}
		assertThat(this.repository.countOtpAttempt(user.id(), "dummy-jti")).isEmpty();
		assertThat(this.repository.countOtpAttempt(UserId.generate(), "dummy-jti")).isEmpty();
	}

	/**
	 * OTP のロックは、jti が同じときだけ書く（ロックの期限を書いて jti を消す）。新しい仮認証に置き換わっていたら何も書かない。
	 */
	@Test
	void savesOtpLockOnlyForMatchingLoginToken() {
		User exhausted = User.restore(UserId.generate(), LoginId.of("dummy_otp_lock"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), 0,
				User.MAX_OTP_FAILURES, Optional.empty(), Optional.of("dummy-jti"), NOW);
		items().putUser(exhausted.passwordVerified("dummy-new-jti"));

		assertThat(this.repository.saveOtpLock(exhausted, exhausted.lockForOtpFailures(NOW))).isFalse();
		assertThat(items().user(exhausted.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.lockedUntil()).isEmpty();
			assertThat(saved.loginTokenId()).hasValue("dummy-new-jti");
		});

		items().putUser(exhausted);
		assertThat(this.repository.saveOtpLock(exhausted, exhausted.lockForOtpFailures(NOW))).isTrue();
		assertThat(items().user(exhausted.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.lockedUntil()).hasValue(NOW.plus(User.LOCK_DURATION));
			assertThat(saved.loginTokenId()).isEmpty();
		});
	}

	private static User newUser(String loginId, int passwordFailureCount) {
		return User.restore(UserId.generate(), LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), passwordFailureCount, 0, Optional.empty(),
				Optional.empty(), NOW);
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.properties);
	}

}
