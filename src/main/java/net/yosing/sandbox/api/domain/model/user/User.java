package net.yosing.sandbox.api.domain.model.user;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;

/**
 * ユーザー（本登録したもの）。
 *
 * <p>ログインの状態（パスワード・OTP の失敗回数、ロックの期限、今の仮認証の jti）は、ログインの API で使う。
 *
 * <p>ロック：パスワードを {@link #MAX_PASSWORD_FAILURES} 回、または OTP を {@link #MAX_OTP_FAILURES} 回続けて間違えたら、
 * アカウントを {@link #LOCK_DURATION} ロックする（ロックの期限は1つで、どちらでロックしたかは区別しない）。
 * 失敗回数は、それぞれ合ったときに0に戻す。OTP の失敗回数は仮認証をまたいで数え、パスワードが合っても戻さない
 * （ログインを始め直して、OTP を試し続けられないようにする）。ロックの期限が過ぎた後の最初のログインの開始で、両方を0から数え直す。
 */
public final class User {

	/**
	 * パスワードの失敗の上限（続けて失敗した回数）。達したらロックする。
	 */
	public static final int MAX_PASSWORD_FAILURES = 5;

	/**
	 * OTP の失敗の上限（仮認証をまたいで、続けて失敗した回数）。達したらロックする。
	 */
	public static final int MAX_OTP_FAILURES = 5;

	/**
	 * ロックの長さ（パスワード・OTP で共通）。
	 */
	public static final Duration LOCK_DURATION = Duration.ofMinutes(15);

	private final UserId id;

	private final LoginId loginId;

	private final PasswordHash passwordHash;

	private final EncryptedTotpSecret totpSecret;

	private final TotpStep lastUsedTotpStep;

	private final int passwordFailureCount;

	private final int otpFailureCount;

	// 無ければ null（ロックしていない）
	private final Instant lockedUntil;

	// 無ければ null（仮認証していない）
	private final String loginTokenId;

	private final Instant createdAt;

	private User(UserId id, LoginId loginId, PasswordHash passwordHash, EncryptedTotpSecret totpSecret,
			TotpStep lastUsedTotpStep, int passwordFailureCount, int otpFailureCount, Instant lockedUntil,
			String loginTokenId, Instant createdAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.loginId = Objects.requireNonNull(loginId, "loginId");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.totpSecret = Objects.requireNonNull(totpSecret, "totpSecret");
		this.lastUsedTotpStep = Objects.requireNonNull(lastUsedTotpStep, "lastUsedTotpStep");
		this.passwordFailureCount = passwordFailureCount;
		this.otpFailureCount = otpFailureCount;
		this.lockedUntil = lockedUntil;
		this.loginTokenId = loginTokenId;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
	}

	/**
	 * 本登録する。失敗回数は0、ロック・仮認証は無しから始める。
	 *
	 * @param id ユーザーの ID（仮登録のときに採番したもの）
	 * @param loginId ログイン ID
	 * @param passwordHash パスワードのハッシュ
	 * @param totpSecret 暗号化した TOTP の秘密情報
	 * @param lastUsedTotpStep 登録の確定で一致したステップ（同じコードをログインで使えないようにする）
	 * @param now 今の日時（秒にそろえる）
	 * @return ユーザー
	 */
	public static User register(UserId id, LoginId loginId, PasswordHash passwordHash, EncryptedTotpSecret totpSecret,
			TotpStep lastUsedTotpStep, Instant now) {
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, 0, 0, null, null,
				now.truncatedTo(ChronoUnit.SECONDS));
	}

	/**
	 * 保存したユーザーを戻す（DB から読んだとき）。
	 *
	 * @param id ユーザーの ID
	 * @param loginId ログイン ID
	 * @param passwordHash パスワードのハッシュ
	 * @param totpSecret 暗号化した TOTP の秘密情報
	 * @param lastUsedTotpStep 最後に使ったステップ
	 * @param passwordFailureCount パスワードの続けての失敗回数
	 * @param otpFailureCount 今の仮認証での OTP の失敗回数
	 * @param lockedUntil ロックの期限（無ければ空）
	 * @param loginTokenId 今の仮認証の jti（無ければ空）
	 * @param createdAt 本登録した日時
	 * @return ユーザー
	 */
	public static User restore(UserId id, LoginId loginId, PasswordHash passwordHash, EncryptedTotpSecret totpSecret,
			TotpStep lastUsedTotpStep, int passwordFailureCount, int otpFailureCount, Optional<Instant> lockedUntil,
			Optional<String> loginTokenId, Instant createdAt) {
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, passwordFailureCount, otpFailureCount,
				lockedUntil.orElse(null), loginTokenId.orElse(null), createdAt);
	}

	/**
	 * ロック中か。ロックの期限の日時になったら解ける。
	 *
	 * @param now 今の日時
	 * @return ロック中なら true
	 */
	public boolean isLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/**
	 * パスワードを照合する前に、その試行を失敗として数える（照合の前に保存し、同時に試されても上限を超えないようにする）。
	 *
	 * <ul>
	 * <li>ロックの期限が過ぎていれば、パスワード・OTP の失敗回数を0に戻してから数える（ロックは解く）</li>
	 * <li>数えた結果が上限に達したら、今から {@link #LOCK_DURATION} ロックする（この試行が成功すれば
	 * {@link #passwordVerified(String)} で解く）</li>
	 * </ul>
	 *
	 * @param now 今の日時
	 * @return 数えた後のユーザー
	 * @throws IllegalStateException ロック中の場合（ロック中は照合しない）
	 */
	public User countPasswordAttempt(Instant now) {
		if (isLocked(now)) {
			throw new IllegalStateException("ロック中は照合しない");
		}
		boolean lockExpired = lockedUntil != null;
		int count = (lockExpired ? 0 : passwordFailureCount) + 1;
		int otpCount = lockExpired ? 0 : otpFailureCount;
		Instant newLockedUntil = count >= MAX_PASSWORD_FAILURES ? lockFrom(now) : null;
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, count, otpCount, newLockedUntil,
				loginTokenId, createdAt);
	}

	/**
	 * パスワードが合った（ログインの開始が成功した）。パスワードの失敗回数を0に戻してロックを解き、新しい仮認証を始める
	 * （前の仮認証の JWT は使えなくなる）。OTP の失敗回数は戻さない（OTP が合ったときだけ戻す）。
	 *
	 * @param newLoginTokenId 新しい仮認証の jti
	 * @return 更新した後のユーザー
	 */
	public User passwordVerified(String newLoginTokenId) {
		Objects.requireNonNull(newLoginTokenId, "newLoginTokenId");
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, 0, otpFailureCount, null,
				newLoginTokenId, createdAt);
	}

	/**
	 * 今の仮認証の jti か（使用済み・置き換わった仮認証の JWT は使えない）。
	 *
	 * @param tokenId JWT の jti
	 * @return 今の仮認証の jti なら true
	 */
	public boolean hasLoginToken(String tokenId) {
		return loginTokenId != null && loginTokenId.equals(tokenId);
	}

	/**
	 * OTP の試行が上限に達したか（達したら、アカウントをロックして仮認証を終える）。
	 *
	 * <p>OTP の失敗回数は、照合の前に試行として数える（同時に試されても上限を超えて照合しないため）。
	 *
	 * @return 達していれば true
	 */
	public boolean otpAttemptsExhausted() {
		return otpFailureCount >= MAX_OTP_FAILURES;
	}

	/**
	 * OTP の失敗が上限に達したので、今から {@link #LOCK_DURATION} ロックし、仮認証を終える。
	 *
	 * @param now 今の日時
	 * @return ロックした後のユーザー
	 * @throws IllegalStateException 上限に達していない場合
	 */
	public User lockForOtpFailures(Instant now) {
		if (!otpAttemptsExhausted()) {
			throw new IllegalStateException("OTP の失敗が上限に達していない");
		}
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, passwordFailureCount, otpFailureCount,
				lockFrom(now), null, createdAt);
	}

	/**
	 * @return OTP の残りの回数（0以上）
	 */
	public int remainingOtpAttempts() {
		return Math.max(0, MAX_OTP_FAILURES - otpFailureCount);
	}

	/**
	 * OTP が合った（ログインの確定が成功した）。一致したステップを最後に使ったステップにし（同じコードを使えないようにする）、
	 * OTP の失敗回数を0に戻して、仮認証を終える。
	 *
	 * @param matchedStep OTP が一致したステップ
	 * @return 更新した後のユーザー
	 * @throws IllegalArgumentException 最後に使ったステップより後でない場合
	 */
	public User otpVerified(TotpStep matchedStep) {
		if (!matchedStep.isAfter(lastUsedTotpStep)) {
			throw new IllegalArgumentException("最後に使ったステップより後のステップにする");
		}
		return new User(id, loginId, passwordHash, totpSecret, matchedStep, passwordFailureCount, 0, lockedUntil, null,
				createdAt);
	}

	// ロックの期限（日時は秒の単位にそろえる）
	private static Instant lockFrom(Instant now) {
		return now.truncatedTo(ChronoUnit.SECONDS).plus(LOCK_DURATION);
	}

	public UserId id() {
		return id;
	}

	public LoginId loginId() {
		return loginId;
	}

	public PasswordHash passwordHash() {
		return passwordHash;
	}

	public EncryptedTotpSecret totpSecret() {
		return totpSecret;
	}

	public TotpStep lastUsedTotpStep() {
		return lastUsedTotpStep;
	}

	public int passwordFailureCount() {
		return passwordFailureCount;
	}

	public int otpFailureCount() {
		return otpFailureCount;
	}

	public Optional<Instant> lockedUntil() {
		return Optional.ofNullable(lockedUntil);
	}

	public Optional<String> loginTokenId() {
		return Optional.ofNullable(loginTokenId);
	}

	public Instant createdAt() {
		return createdAt;
	}

}
