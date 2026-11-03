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
 * パスワードは {@link #MAX_PASSWORD_FAILURES} 回続けて失敗したら {@link #PASSWORD_LOCK_DURATION} ロックする。
 */
public final class User {

	/**
	 * パスワードの失敗の上限（続けて失敗した回数）。達したらロックする。
	 */
	public static final int MAX_PASSWORD_FAILURES = 5;

	/**
	 * ロックの長さ。
	 */
	public static final Duration PASSWORD_LOCK_DURATION = Duration.ofMinutes(15);

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
	public boolean isPasswordLocked(Instant now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	/**
	 * パスワードを照合する前に、その試行を失敗として数える（照合の前に保存し、同時に試されても上限を超えないようにする）。
	 *
	 * <ul>
	 * <li>ロックの期限が過ぎていれば、失敗回数を0に戻してから数える</li>
	 * <li>数えた結果が上限に達したら、今から {@link #PASSWORD_LOCK_DURATION} ロックする（この試行が成功すれば
	 * {@link #passwordVerified(String)} で解く）</li>
	 * </ul>
	 *
	 * @param now 今の日時
	 * @return 数えた後のユーザー
	 * @throws IllegalStateException ロック中の場合（ロック中は照合しない）
	 */
	public User countPasswordAttempt(Instant now) {
		if (isPasswordLocked(now)) {
			throw new IllegalStateException("ロック中は照合しない");
		}
		int count = (lockedUntil == null ? passwordFailureCount : 0) + 1;
		Instant newLockedUntil = count >= MAX_PASSWORD_FAILURES
				? now.truncatedTo(ChronoUnit.SECONDS).plus(PASSWORD_LOCK_DURATION) : null;
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, count, otpFailureCount,
				newLockedUntil, loginTokenId, createdAt);
	}

	/**
	 * パスワードが合った（ログインの開始が成功した）。失敗回数を0に戻してロックを解き、新しい仮認証を始める
	 * （前の仮認証の JWT は使えなくなる）。
	 *
	 * @param newLoginTokenId 新しい仮認証の jti
	 * @return 更新した後のユーザー
	 */
	public User passwordVerified(String newLoginTokenId) {
		Objects.requireNonNull(newLoginTokenId, "newLoginTokenId");
		return new User(id, loginId, passwordHash, totpSecret, lastUsedTotpStep, 0, 0, null, newLoginTokenId,
				createdAt);
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
