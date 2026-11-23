package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.session.Session;
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
 * ログイン確定のトランザクション（ユーザーの条件付き更新とセッションの作成を、すべて行うか何もしないか）を、DynamoDB Local で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbLoginCompletionStoreTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:05:00Z");

	@Autowired
	DynamoDbLoginCompletionStore store;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * 一致したステップを保存し、OTP の失敗回数を0に戻して jti を消し、セッションを作る。
	 */
	@Test
	void updatesUserAndCreatesSession() {
		User current = putLoggingInUser("dummy_login_store_ok", "dummy-jti");
		Session session = Session.issue(current.id(), NOW).session();

		assertThat(this.store.complete(current, current.otpVerified(new TotpStep(124)), session)).isTrue();

		assertThat(items().user(current.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.lastUsedTotpStep()).isEqualTo(new TotpStep(124));
			assertThat(saved.otpFailureCount()).isZero();
			assertThat(saved.loginTokenId()).isEmpty();
		});
		assertThat(items().sessionItem(session.idHash())).isPresent();
	}

	/**
	 * 同じステップが先に使われていたら（最後に使ったステップが一致したステップ以降）、false で何も書かない。
	 */
	@Test
	void failsWhenStepAlreadyUsed() {
		User current = putLoggingInUser("dummy_login_store_used", "dummy-jti");
		Session first = Session.issue(current.id(), NOW).session();
		Session second = Session.issue(current.id(), NOW).session();
		this.store.complete(current, current.otpVerified(new TotpStep(124)), first);

		assertThat(this.store.complete(current, current.otpVerified(new TotpStep(124)), second)).isFalse();

		assertThat(items().sessionItem(second.idHash())).isEmpty();
	}

	/**
	 * 仮認証が置き換わっていたら（jti が違う）、false で何も書かない。
	 */
	@Test
	void failsWhenLoginTokenReplaced() {
		User current = putLoggingInUser("dummy_login_store_replaced", "dummy-jti");
		items().putUser(current.passwordVerified("dummy-new-jti"));
		Session session = Session.issue(current.id(), NOW).session();

		assertThat(this.store.complete(current, current.otpVerified(new TotpStep(124)), session)).isFalse();

		assertThat(items().user(current.id())).hasValueSatisfying((saved) -> {
			assertThat(saved.lastUsedTotpStep()).isEqualTo(new TotpStep(123));
			assertThat(saved.loginTokenId()).hasValue("dummy-new-jti");
		});
		assertThat(items().sessionItem(session.idHash())).isEmpty();
	}

	// パスワードが合って、仮認証（jti）が始まっているユーザー（最後に使ったステップは 123）
	private User putLoggingInUser(String loginId, String loginTokenId) {
		User user = User
			.register(UserId.generate(), LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"),
					new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123), NOW)
			.passwordVerified(loginTokenId);
		items().putUser(user);
		return user;
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(this.dynamoDb, this.properties);
	}

}
