package net.yosing.sandbox.api.infrastructure.dynamodb;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * 仮登録の保存の条件（同じ登録 ID の項目を上書きしない）を、DynamoDB Local で確かめる。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbRegistrationRepositoryTests {

	@Autowired
	DynamoDbRegistrationRepository repository;

	/**
	 * 同じ登録 ID の仮登録をもう一度保存すると、条件付き書き込みで失敗する。
	 */
	@Test
	void doesNotOverwriteExistingRegistration() {
		Registration registration = Registration.start(RegistrationId.generate(), UserId.generate(),
				LoginId.of("dummy_repository"), new PasswordHash("{bcrypt}dummy-hash"),
				new EncryptedTotpSecret(new byte[] { 1 }), Instant.parse("2026-11-01T00:00:00Z"));
		repository.add(registration);

		assertThatExceptionOfType(ConditionalCheckFailedException.class).isThrownBy(() -> repository.add(registration));
	}

}
