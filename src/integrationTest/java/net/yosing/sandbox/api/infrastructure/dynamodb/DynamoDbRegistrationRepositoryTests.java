package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 仮登録の読み書き（保存の条件、項目の変換の往復、OTP の試行の数え方、削除）を、DynamoDB Local で確かめる。
 *
 * <p>登録 ID はテストごとにランダムに作るため、テーブルを共有してもぶつからない。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbRegistrationRepositoryTests {

	@Autowired
	DynamoDbRegistrationRepository repository;

	/**
	 * 保存した仮登録を読むと、同じ値に戻る（項目の変換の往復）。
	 */
	@Test
	void findsWhatWasAdded() {
		Registration registration = newRegistration();
		repository.add(registration);

		assertThat(repository.findById(registration.id())).hasValueSatisfying(found -> {
			assertThat(found.id()).isEqualTo(registration.id());
			assertThat(found.userId()).isEqualTo(registration.userId());
			assertThat(found.loginId()).isEqualTo(registration.loginId());
			assertThat(found.passwordHash()).isEqualTo(registration.passwordHash());
			assertThat(found.totpSecret()).isEqualTo(registration.totpSecret());
			assertThat(found.otpFailureCount()).isZero();
			assertThat(found.createdAt()).isEqualTo(registration.createdAt());
			assertThat(found.expiresAt()).isEqualTo(registration.expiresAt());
		});
		assertThat(repository.findById(RegistrationId.generate())).isEmpty();
	}

	/**
	 * 同じ登録 ID の仮登録をもう一度保存すると、条件付き書き込みで失敗する。
	 */
	@Test
	void doesNotOverwriteExistingRegistration() {
		Registration registration = newRegistration();
		repository.add(registration);

		assertThatExceptionOfType(ConditionalCheckFailedException.class).isThrownBy(() -> repository.add(registration));
	}

	/**
	 * OTP の試行は、呼ぶたびに DB の値に1ずつ加わり、上限（5回）に達したら数えない。仮登録が無ければ空を返す（項目を作らない）。
	 */
	@Test
	void countsOtpAttemptUpToLimit() {
		Registration registration = newRegistration();
		repository.add(registration);

		for (int attempt = 1; attempt <= Registration.MAX_OTP_FAILURES; attempt++) {
			int expected = attempt;
			assertThat(repository.countOtpAttempt(registration.id()))
				.hasValueSatisfying(found -> assertThat(found.otpFailureCount()).isEqualTo(expected));
		}
		assertThat(repository.countOtpAttempt(registration.id())).isEmpty();

		RegistrationId missing = RegistrationId.generate();
		assertThat(repository.countOtpAttempt(missing)).isEmpty();
		assertThat(repository.findById(missing)).isEmpty();
	}

	/**
	 * 削除すると読めなくなる。無いものを削除しても失敗しない。
	 */
	@Test
	void deletesRegistration() {
		Registration registration = newRegistration();
		repository.add(registration);

		repository.delete(registration.id());
		repository.delete(registration.id());

		assertThat(repository.findById(registration.id())).isEmpty();
	}

	private static Registration newRegistration() {
		return Registration.start(RegistrationId.generate(), UserId.generate(), LoginId.of("dummy_repository"),
				new PasswordHash("{bcrypt}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1, 2, 3 }),
				Instant.parse("2026-11-01T00:00:00Z"));
	}

}
