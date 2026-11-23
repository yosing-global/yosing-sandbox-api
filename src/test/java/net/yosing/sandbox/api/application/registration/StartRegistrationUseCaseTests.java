package net.yosing.sandbox.api.application.registration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import net.yosing.sandbox.api.application.port.FakePasswordHasher;
import net.yosing.sandbox.api.application.port.FakeTotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.InMemoryRegistrationRepository;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpauthUri;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.InMemoryUserRepository;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 登録の開始の流れ（仮登録の保存と、設定 URI の返し方、使用済みのログイン ID の扱い）を確かめる。
 */
class StartRegistrationUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	private final InMemoryUserRepository users = new InMemoryUserRepository();

	private final InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository();

	private final FakeTotpSecretEncryptor encryptor = new FakeTotpSecretEncryptor();

	private final StartRegistrationUseCase useCase = new StartRegistrationUseCase(this.users, this.registrations,
			new FakePasswordHasher(), this.encryptor, Clock.fixed(NOW, ZoneOffset.UTC),
			new OtpauthProperties("dummy-sandbox"));

	private final StartRegistrationCommand command = new StartRegistrationCommand(LoginId.of("dummy_alice"),
			new Password("dummy correct horse battery"));

	/**
	 * 仮登録（ハッシュにしたパスワード、userId で暗号化した秘密情報、10分後の期限）を保存し、
	 * 同じ秘密情報の設定 URI と、登録 ID・期限を返す。
	 */
	@Test
	void savesRegistrationAndReturnsOtpauthUri() {
		StartRegistrationResult result = this.useCase.start(this.command);

		assertThat(this.registrations.all()).hasSize(1);
		Registration saved = this.registrations.all().get(0);
		assertThat(saved.id()).isEqualTo(result.registrationId());
		assertThat(saved.loginId()).isEqualTo(LoginId.of("dummy_alice"));
		assertThat(saved.passwordHash()).isEqualTo(new PasswordHash("{dummy}dummy correct horse battery"));
		assertThat(saved.expiresAt()).isEqualTo(NOW.plus(Registration.VALIDITY)).isEqualTo(result.expiresAt());

		TotpSecret secret = this.encryptor.decrypt(saved.totpSecret(), saved.userId());
		assertThat(result.otpauthUri()).isEqualTo(OtpauthUri.create("dummy-sandbox", "dummy_alice", secret));
	}

	/**
	 * 本登録済みのログイン ID では始められず、仮登録も保存しない。
	 */
	@Test
	void rejectsTakenLoginId() {
		this.users.addLoginId(LoginId.of("dummy_alice"));

		assertThatExceptionOfType(LoginIdTakenException.class).isThrownBy(() -> this.useCase.start(this.command));
		assertThat(this.registrations.all()).isEmpty();
	}

}
