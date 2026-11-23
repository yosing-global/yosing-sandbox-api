package net.yosing.sandbox.api.application.registration;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.stream.Stream;

import net.yosing.sandbox.api.application.error.OtpAttemptsExceededException;
import net.yosing.sandbox.api.application.error.OtpInvalidException;
import net.yosing.sandbox.api.application.port.FakeRegistrationCompletionStore;
import net.yosing.sandbox.api.application.port.FakeRegistrationCompletionStore.Completion;
import net.yosing.sandbox.api.application.port.FakeTotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.InMemoryRegistrationRepository;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.UserId;
import net.yosing.sandbox.api.domain.service.TotpVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * 登録確定の流れ（成功・OTP の失敗と上限・仮登録が無い・ログイン ID の先取り）を確かめる。
 */
class VerifyRegistrationUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:05:00Z");

	private static final TotpSecret SECRET = new TotpSecret("dummy-totp-secret-20".getBytes(StandardCharsets.US_ASCII));

	private final InMemoryRegistrationRepository registrations = new InMemoryRegistrationRepository();

	private final FakeRegistrationCompletionStore completionStore = new FakeRegistrationCompletionStore();

	private final FakeTotpSecretEncryptor encryptor = new FakeTotpSecretEncryptor();

	private final TotpVerifier totpVerifier = new TotpVerifier();

	private final VerifyRegistrationUseCase useCase = new VerifyRegistrationUseCase(this.registrations,
			this.completionStore, this.encryptor, this.totpVerifier, Clock.fixed(NOW, ZoneOffset.UTC));

	private final Registration registration = startRegistration(Instant.parse("2026-11-01T00:00:00Z"));

	private final OtpCode validCode = this.totpVerifier.generate(SECRET, TotpStep.at(NOW));

	private final OtpCode wrongCode = codeOutsideWindow();

	/**
	 * 正しいコードなら、仮登録からユーザーを作り（一致したステップを保存）、セッションを発行して保存し、
	 * セッション ID（保存したハッシュの元の値）と期限を返す。
	 */
	@Test
	void completesRegistration() {
		VerifyRegistrationResult result = this.useCase.verify(command(this.validCode));

		assertThat(this.completionStore.completions()).hasSize(1);
		Completion completion = this.completionStore.completions().get(0);
		assertThat(completion.registration().id()).isEqualTo(this.registration.id());
		assertThat(completion.user().id()).isEqualTo(this.registration.userId());
		assertThat(completion.user().lastUsedTotpStep()).isEqualTo(TotpStep.at(NOW));
		assertThat(completion.session().userId()).isEqualTo(this.registration.userId());
		assertThat(completion.session().idHash()).isEqualTo(result.sessionId().hash());
		assertThat(result.sessionExpiresAt()).isEqualTo(completion.session().expiresAt());
	}

	/**
	 * コードが違えば、失敗回数を増やし、残りの回数を付けて失敗する（仮登録は残す）。
	 */
	@Test
	void recordsOtpFailure() {
		assertThatExceptionOfType(OtpInvalidException.class)
			.isThrownBy(() -> this.useCase.verify(command(this.wrongCode)))
			.returns(4, OtpInvalidException::remainingAttempts);
		assertThat(this.registrations.findById(this.registration.id()))
			.hasValueSatisfying((found) -> assertThat(found.otpFailureCount()).isEqualTo(1));
		assertThat(this.completionStore.completions()).isEmpty();
	}

	/**
	 * 5回目の失敗で、仮登録を削除して上限の失敗にする。
	 */
	@Test
	void deletesRegistrationAtFifthFailure() {
		for (int attempt = 1; attempt <= 4; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> this.useCase.verify(command(this.wrongCode)));
		}

		assertThatExceptionOfType(OtpAttemptsExceededException.class)
			.isThrownBy(() -> this.useCase.verify(command(this.wrongCode)));
		assertThat(this.registrations.findById(this.registration.id())).isEmpty();
	}

	/**
	 * 4回間違えた後でも、5回目に正しいコードなら登録できる（照合の前に数えても、最後の1回は照合する）。
	 */
	@Test
	void completesAtFifthAttempt() {
		for (int attempt = 1; attempt < Registration.MAX_OTP_FAILURES; attempt++) {
			assertThatExceptionOfType(OtpInvalidException.class)
				.isThrownBy(() -> this.useCase.verify(command(this.wrongCode)));
		}

		this.useCase.verify(command(this.validCode));

		assertThat(this.completionStore.completions()).hasSize(1);
	}

	/**
	 * 読んだ後に、同時のほかの試行で仮登録が消えていたら（試行を数えられない）、照合せずに見つからない失敗にする。
	 */
	@Test
	void rejectsWhenRegistrationGoneBeforeCounting() {
		InMemoryRegistrationRepository racing = new InMemoryRegistrationRepository() {
			@Override
			public Optional<Registration> countOtpAttempt(RegistrationId id) {
				delete(id);
				return super.countOtpAttempt(id);
			}
		};
		racing.add(this.registration);
		VerifyRegistrationUseCase racingUseCase = new VerifyRegistrationUseCase(racing, this.completionStore,
				this.encryptor, this.totpVerifier, Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatExceptionOfType(RegistrationNotFoundException.class)
			.isThrownBy(() -> racingUseCase.verify(command(this.validCode)));
		assertThat(this.completionStore.completions()).isEmpty();
	}

	/**
	 * 無い登録 ID・期限切れの仮登録は、見つからない失敗にする。
	 */
	@Test
	void rejectsMissingOrExpiredRegistration() {
		assertThatExceptionOfType(RegistrationNotFoundException.class).isThrownBy(
				() -> this.useCase.verify(new VerifyRegistrationCommand(RegistrationId.generate(), this.validCode)));

		Registration expired = startRegistration(NOW.minus(Registration.VALIDITY));
		assertThatExceptionOfType(RegistrationNotFoundException.class)
			.isThrownBy(() -> this.useCase.verify(new VerifyRegistrationCommand(expired.id(), this.validCode)));
	}

	/**
	 * 同じログイン ID が先に本登録されていたら、その失敗をそのまま返す。
	 */
	@Test
	void propagatesLoginIdTaken() {
		this.completionStore.failWith(new LoginIdTakenException());

		assertThatExceptionOfType(LoginIdTakenException.class)
			.isThrownBy(() -> this.useCase.verify(command(this.validCode)));
	}

	// 前後1ステップのどのコードとも違うコード（偶然一致しないようにする）
	private OtpCode codeOutsideWindow() {
		TotpStep step = TotpStep.at(NOW);
		return Stream.of("000000", "111111", "222222", "333333")
			.map(OtpCode::new)
			.filter((code) -> this.totpVerifier.verify(SECRET, code, step, Optional.empty()).isEmpty())
			.findFirst()
			.orElseThrow();
	}

	private VerifyRegistrationCommand command(OtpCode code) {
		return new VerifyRegistrationCommand(this.registration.id(), code);
	}

	private Registration startRegistration(Instant startedAt) {
		UserId userId = UserId.generate();
		Registration started = Registration.start(RegistrationId.generate(), userId, LoginId.of("dummy_alice"),
				new PasswordHash("{dummy}dummy-hash"), this.encryptor.encrypt(SECRET, userId), startedAt);
		this.registrations.add(started);
		return started;
	}

}
