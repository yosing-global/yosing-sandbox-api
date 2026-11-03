package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.registration.LoginIdTakenException;
import net.yosing.sandbox.api.application.registration.RegistrationNotFoundException;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
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
 * 登録の確定のトランザクション（すべて書くか何も書かないか、取り消しの理由の見分け）を、DynamoDB Local で確かめる。
 *
 * <p>テーブルはテストの間で共有するため、テストごとに違うログイン ID を使う。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbRegistrationCompletionStoreTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:05:00Z");

	@Autowired
	DynamoDbRegistrationCompletionStore store;

	@Autowired
	DynamoDbRegistrationRepository registrations;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * ユーザー・ログイン ID・セッションを作り、仮登録を削除する。
	 */
	@Test
	void writesAllItems() {
		Registration registration = addRegistration("dummy_store_ok");
		User user = registration.complete(new TotpStep(123), NOW);
		Session session = Session.issue(user.id(), NOW).session();

		store.complete(registration, user, session);

		DynamoDbTestItems items = items();
		assertThat(items.userItem(user.id())).hasValueSatisfying(item -> {
			assertThat(item.get(UserItemMapper.LOGIN_ID).s()).isEqualTo("dummy_store_ok");
			assertThat(item.get(UserItemMapper.LAST_USED_TOTP_STEP).n()).isEqualTo("123");
			assertThat(item).doesNotContainKeys(UserItemMapper.LOCKED_UNTIL, UserItemMapper.LOGIN_TOKEN_ID);
		});
		assertThat(items.loginIdItem(user.loginId())).hasValueSatisfying(
				item -> assertThat(item.get(UserItemMapper.USER_ID).s()).isEqualTo(user.id().toString()));
		assertThat(items.sessionItem(session.idHash())).hasValueSatisfying(
				item -> assertThat(item.get(SessionItemMapper.USER_ID).s()).isEqualTo(user.id().toString()));
		assertThat(items.registration(registration.id())).isEmpty();
	}

	/**
	 * ログイン ID が先に使われていたら LoginIdTakenException で、何も書かない（仮登録も残る）。
	 */
	@Test
	void failsWhenLoginIdTaken() {
		Registration registration = addRegistration("dummy_store_taken");
		items().putLoginId(registration.loginId(), UserId.generate());
		User user = registration.complete(new TotpStep(123), NOW);
		IssuedSession issued = Session.issue(user.id(), NOW);

		assertThatExceptionOfType(LoginIdTakenException.class)
			.isThrownBy(() -> store.complete(registration, user, issued.session()));

		assertThat(items().userItem(user.id())).isEmpty();
		assertThat(items().sessionItem(issued.session().idHash())).isEmpty();
		assertThat(items().registration(registration.id())).isPresent();
	}

	/**
	 * 仮登録が既に無ければ RegistrationNotFoundException で、何も書かない（同じ仮登録の二重の確定を防ぐ）。
	 */
	@Test
	void failsWhenRegistrationMissing() {
		Registration registration = Registration.start(RegistrationId.generate(), UserId.generate(),
				LoginId.of("dummy_store_missing"), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), NOW);
		User user = registration.complete(new TotpStep(123), NOW);

		assertThatExceptionOfType(RegistrationNotFoundException.class)
			.isThrownBy(() -> store.complete(registration, user, Session.issue(user.id(), NOW).session()));

		assertThat(items().userItem(user.id())).isEmpty();
		assertThat(items().loginIdItem(user.loginId())).isEmpty();
	}

	private Registration addRegistration(String loginId) {
		Registration registration = Registration.start(RegistrationId.generate(), UserId.generate(),
				LoginId.of(loginId), new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }),
				NOW);
		registrations.add(registration);
		return registration;
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(dynamoDb, properties);
	}

}
