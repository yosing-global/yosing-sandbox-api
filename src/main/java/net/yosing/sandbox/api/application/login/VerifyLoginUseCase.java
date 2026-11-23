package net.yosing.sandbox.api.application.login;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.application.error.OtpAttemptsExceededException;
import net.yosing.sandbox.api.application.error.OtpInvalidException;
import net.yosing.sandbox.api.application.port.LoginCompletionStore;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.application.port.TotpSecretEncryptor;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserRepository;
import net.yosing.sandbox.api.domain.service.TotpVerifier;

import org.springframework.stereotype.Service;

/**
 * ログインの確定（POST /v1/logins/verify）
 *
 * <ol>
 * <li>仮認証 JWT（署名・aud・期限）を検証し、ユーザーを取り出して jti が今の仮認証のものかを確かめる。違えば 401
 * {@code LOGIN_TOKEN_INVALID}</li>
 * <li>OTP の試行が上限に達していれば、アカウントをロックして 429</li>
 * <li>照合の前に、OTP の試行を数える（同時に試されても、上限を超えて照合しない）。失敗回数は仮認証をまたいで数える</li>
 * <li>秘密情報を復号し、OTP を照合する（前後1ステップを許し、最後に使ったステップより後であること）</li>
 * <li>失敗：上限（続けて5回）に達したらアカウントを15分ロックし、仮認証を終えて 429、それ以外は 401（残りの回数を付ける）</li>
 * <li>成功：最後に使ったステップの条件付き更新とセッションの作成を1つのトランザクションで行い、セッション ID を返す。
 * 条件に合わなければ（同じコードが先に使われた）失敗として扱う</li>
 * </ol>
 */
@Service
public class VerifyLoginUseCase {

	private final LoginTokenSigner loginTokenSigner;

	private final UserRepository users;

	private final LoginCompletionStore completionStore;

	private final TotpSecretEncryptor totpSecretEncryptor;

	private final TotpVerifier totpVerifier;

	private final Clock clock;

	public VerifyLoginUseCase(LoginTokenSigner loginTokenSigner, UserRepository users,
			LoginCompletionStore completionStore, TotpSecretEncryptor totpSecretEncryptor, TotpVerifier totpVerifier,
			Clock clock) {
		this.loginTokenSigner = loginTokenSigner;
		this.users = users;
		this.completionStore = completionStore;
		this.totpSecretEncryptor = totpSecretEncryptor;
		this.totpVerifier = totpVerifier;
		this.clock = clock;
	}

	/**
	 * ログインを確定する。
	 * @param command 入力
	 * @return セッション ID と期限
	 * @throws LoginTokenInvalidException 仮認証 JWT が無効・期限切れ・使用済みの場合
	 * @throws OtpInvalidException OTP コードが違う、または使用済みの場合
	 * @throws OtpAttemptsExceededException OTP の試行が上限に達した場合
	 */
	public VerifyLoginResult verify(VerifyLoginCommand command) {
		Instant now = this.clock.instant();
		LoginTokenClaims claims = this.loginTokenSigner.verify(command.loginToken(), now)
			.orElseThrow(LoginTokenInvalidException::new);
		String tokenId = claims.tokenId();
		User user = this.users.findById(claims.userId())
			.filter((found) -> found.hasLoginToken(tokenId))
			.orElseThrow(LoginTokenInvalidException::new);
		if (user.otpAttemptsExhausted()) {
			// 上限に達したときにロックできていなかった場合（その間に新しい仮認証が始まった等）
			throw lockForOtpFailures(user, now);
		}

		// 同時のほかの試行で、仮認証が終わったか上限に達していれば数えられない
		User counted = this.users.countOtpAttempt(user.id(), tokenId).orElseThrow(LoginTokenInvalidException::new);
		TotpSecret secret = this.totpSecretEncryptor.decrypt(user.totpSecret(), user.id());
		Optional<TotpStep> matchedStep = this.totpVerifier.verify(secret, command.code(), TotpStep.at(now),
				Optional.of(user.lastUsedTotpStep()));
		if (matchedStep.isEmpty()) {
			throw otpFailure(counted, now);
		}

		IssuedSession issued = Session.issue(user.id(), now);
		if (!this.completionStore.complete(counted, counted.otpVerified(matchedStep.get()), issued.session())) {
			// 同じコード（ステップ）が同時に使われた、または仮認証が置き換わった
			throw otpFailure(counted, now);
		}
		return new VerifyLoginResult(issued.sessionId(), issued.session().expiresAt());
	}

	private RuntimeException otpFailure(User counted, Instant now) {
		if (counted.otpAttemptsExhausted()) {
			return lockForOtpFailures(counted, now);
		}
		return new OtpInvalidException(counted.remainingOtpAttempts());
	}

	// アカウントをロックし、仮認証を終える。仮認証が置き換わっていて書けなくても、失敗回数が上限のため照合には進めない
	private OtpAttemptsExceededException lockForOtpFailures(User current, Instant now) {
		this.users.saveOtpLock(current, current.lockForOtpFailures(now));
		return new OtpAttemptsExceededException();
	}

}
