package net.yosing.sandbox.api.application.registration;

import java.time.Clock;

import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.registration.RegistrationRepository;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpauthUri;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.domain.model.user.UserRepository;

import org.springframework.stereotype.Service;

/**
 * 登録の開始（POST /v1/registrations）。
 *
 * <p>ログイン ID が使われていないことを確かめ、TOTP の秘密情報を作って仮登録を保存し、認証アプリの設定 URI を返す。
 * 秘密情報は、仮登録のときに採番した userId で暗号化する。
 */
@Service
public class StartRegistrationUseCase {

	private final UserRepository users;

	private final RegistrationRepository registrations;

	private final PasswordHasher passwordHasher;

	private final TotpSecretEncryptor totpSecretEncryptor;

	private final Clock clock;

	private final String issuer;

	public StartRegistrationUseCase(UserRepository users, RegistrationRepository registrations,
			PasswordHasher passwordHasher, TotpSecretEncryptor totpSecretEncryptor, Clock clock,
			OtpauthProperties otpauthProperties) {
		this.users = users;
		this.registrations = registrations;
		this.passwordHasher = passwordHasher;
		this.totpSecretEncryptor = totpSecretEncryptor;
		this.clock = clock;
		this.issuer = otpauthProperties.issuer();
	}

	/**
	 * 登録を始める。
	 *
	 * @param command 入力
	 * @return 登録 ID・設定 URI・期限
	 * @throws LoginIdTakenException ログイン ID が使われている場合
	 */
	public StartRegistrationResult start(StartRegistrationCommand command) {
		if (users.existsByLoginId(command.loginId())) {
			throw new LoginIdTakenException();
		}
		PasswordHash passwordHash = passwordHasher.hash(command.password());
		TotpSecret secret = TotpSecret.generate();
		UserId userId = UserId.generate();
		Registration registration = Registration.start(RegistrationId.generate(), userId, command.loginId(),
				passwordHash, totpSecretEncryptor.encrypt(secret, userId), clock.instant());
		registrations.add(registration);
		return new StartRegistrationResult(registration.id(),
				OtpauthUri.create(issuer, command.loginId().value(), secret), registration.expiresAt());
	}

}
