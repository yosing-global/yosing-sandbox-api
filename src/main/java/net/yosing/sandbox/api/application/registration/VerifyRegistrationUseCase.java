package net.yosing.sandbox.api.application.registration;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.application.error.OtpAttemptsExceededException;
import net.yosing.sandbox.api.application.error.OtpInvalidException;
import net.yosing.sandbox.api.application.port.RegistrationCompletionStore;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationRepository;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.service.TotpVerifier;

import org.springframework.stereotype.Service;

/**
 * 登録の確定（POST /v1/registrations/{registrationId}/verify）。
 *
 * <ol>
 * <li>仮登録を取り出す。無い・期限切れなら 404</li>
 * <li>照合の前に、OTP の試行を数える（同時に試されても、上限を超えて照合しない）。数えられなければ 404</li>
 * <li>秘密情報を復号し、OTP を照合する（前後1ステップを許す）</li>
 * <li>失敗：上限に達したら仮登録を削除して 429、それ以外は 401（残りの回数を付ける）</li>
 * <li>成功：ユーザー・ログイン ID・セッションの作成と、仮登録の削除を1つのトランザクションで行い、セッション ID を返す</li>
 * </ol>
 */
@Service
public class VerifyRegistrationUseCase {

	private final RegistrationRepository registrations;

	private final RegistrationCompletionStore completionStore;

	private final TotpSecretEncryptor totpSecretEncryptor;

	private final TotpVerifier totpVerifier;

	private final Clock clock;

	public VerifyRegistrationUseCase(RegistrationRepository registrations, RegistrationCompletionStore completionStore,
			TotpSecretEncryptor totpSecretEncryptor, TotpVerifier totpVerifier, Clock clock) {
		this.registrations = registrations;
		this.completionStore = completionStore;
		this.totpSecretEncryptor = totpSecretEncryptor;
		this.totpVerifier = totpVerifier;
		this.clock = clock;
	}

	/**
	 * 登録を確定する。
	 *
	 * @param command 入力
	 * @return セッション ID と期限
	 * @throws RegistrationNotFoundException 仮登録が無い・期限切れの場合
	 * @throws OtpInvalidException OTP コードが違う場合
	 * @throws OtpAttemptsExceededException OTP の失敗が上限に達した場合
	 * @throws LoginIdTakenException 同じログイン ID が先に本登録されていた場合
	 */
	public VerifyRegistrationResult verify(VerifyRegistrationCommand command) {
		Instant now = clock.instant();
		Registration registration = registrations.findById(command.registrationId())
			.filter(found -> !found.isExpired(now))
			.orElseThrow(RegistrationNotFoundException::new);
		if (registration.otpAttemptsExhausted()) {
			// 上限に達したときに削除できていなかった場合
			registrations.delete(registration.id());
			throw new OtpAttemptsExceededException();
		}

		// 照合の前に試行を数える。同時のほかの試行で、仮登録が消えたか上限に達していれば数えられない
		Registration counted = registrations.countOtpAttempt(registration.id())
			.orElseThrow(RegistrationNotFoundException::new);
		TotpSecret secret = totpSecretEncryptor.decrypt(registration.totpSecret(), registration.userId());
		Optional<TotpStep> matchedStep = totpVerifier.verify(secret, command.code(), TotpStep.at(now),
				Optional.empty());
		if (matchedStep.isEmpty()) {
			throw otpFailure(counted);
		}

		User user = registration.complete(matchedStep.get(), now);
		IssuedSession issued = Session.issue(user.id(), now);
		completionStore.complete(registration, user, issued.session());
		return new VerifyRegistrationResult(issued.sessionId(), issued.session().expiresAt());
	}

	private RuntimeException otpFailure(Registration counted) {
		if (counted.otpAttemptsExhausted()) {
			registrations.delete(counted.id());
			return new OtpAttemptsExceededException();
		}
		return new OtpInvalidException(counted.remainingOtpAttempts());
	}

}
