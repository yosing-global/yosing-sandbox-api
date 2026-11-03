package net.yosing.sandbox.api.domain.model.registration;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * 仮登録（登録の途中）。OTP の確認が済んだら、ユーザーとログイン ID に置き換えて削除する。
 *
 * <p>userId は仮登録のときに採番する（TOTP の秘密情報の暗号化コンテキストに使うため、本登録で暗号化し直さない）。
 * 同じログイン ID の仮登録が複数あってもよい（本登録できるのは先の1件だけ）。
 */
public final class Registration {

	/**
	 * 仮登録の有効期間。
	 */
	public static final Duration VALIDITY = Duration.ofMinutes(10);

	/**
	 * OTP の失敗の上限（1回の仮登録につき）。
	 */
	public static final int MAX_OTP_FAILURES = 5;

	private final RegistrationId id;

	private final UserId userId;

	private final LoginId loginId;

	private final PasswordHash passwordHash;

	private final EncryptedTotpSecret totpSecret;

	private final int otpFailureCount;

	private final Instant createdAt;

	private final Instant expiresAt;

	private Registration(RegistrationId id, UserId userId, LoginId loginId, PasswordHash passwordHash,
			EncryptedTotpSecret totpSecret, int otpFailureCount, Instant createdAt, Instant expiresAt) {
		this.id = Objects.requireNonNull(id, "id");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.loginId = Objects.requireNonNull(loginId, "loginId");
		this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
		this.totpSecret = Objects.requireNonNull(totpSecret, "totpSecret");
		this.otpFailureCount = otpFailureCount;
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
	}

	/**
	 * 仮登録を始める。期限は今から10分後。
	 *
	 * <p>日時は秒の単位にそろえる（DynamoDB の TTL と API の応答が秒の単位のため）。
	 *
	 * @param id 登録 ID
	 * @param userId 本登録したときのユーザーの ID
	 * @param loginId ログイン ID
	 * @param passwordHash パスワードのハッシュ
	 * @param totpSecret 暗号化した TOTP の秘密情報（userId で暗号化したもの）
	 * @param now 今の日時
	 * @return 仮登録
	 */
	public static Registration start(RegistrationId id, UserId userId, LoginId loginId, PasswordHash passwordHash,
			EncryptedTotpSecret totpSecret, Instant now) {
		Instant createdAt = now.truncatedTo(ChronoUnit.SECONDS);
		return new Registration(id, userId, loginId, passwordHash, totpSecret, 0, createdAt,
				createdAt.plus(VALIDITY));
	}

	/**
	 * 保存した仮登録を戻す（DB から読んだとき）。
	 *
	 * @param id 登録 ID
	 * @param userId 本登録したときのユーザーの ID
	 * @param loginId ログイン ID
	 * @param passwordHash パスワードのハッシュ
	 * @param totpSecret 暗号化した TOTP の秘密情報
	 * @param otpFailureCount OTP の失敗回数
	 * @param createdAt 作成日時
	 * @param expiresAt 期限
	 * @return 仮登録
	 */
	public static Registration restore(RegistrationId id, UserId userId, LoginId loginId, PasswordHash passwordHash,
			EncryptedTotpSecret totpSecret, int otpFailureCount, Instant createdAt, Instant expiresAt) {
		return new Registration(id, userId, loginId, passwordHash, totpSecret, otpFailureCount, createdAt, expiresAt);
	}

	/**
	 * 期限が切れているか。期限の日時になったら切れている。
	 *
	 * @param now 今の日時
	 * @return 切れていれば true
	 */
	public boolean isExpired(Instant now) {
		return !now.isBefore(expiresAt);
	}

	/**
	 * OTP の失敗が上限に達したか（達したら、仮登録を削除して最初からやり直してもらう）。
	 *
	 * @return 達していれば true
	 */
	public boolean otpAttemptsExhausted() {
		return otpFailureCount >= MAX_OTP_FAILURES;
	}

	/**
	 * @return OTP の残りの回数（0以上）
	 */
	public int remainingOtpAttempts() {
		return Math.max(0, MAX_OTP_FAILURES - otpFailureCount);
	}

	/**
	 * 本登録する（OTP の確認が済んだとき）。ユーザーの ID・ログイン ID・パスワード・秘密情報は、仮登録のものを引き継ぐ。
	 *
	 * @param matchedStep OTP が一致したステップ（同じコードをログインで使えないよう、最後に使ったステップにする）
	 * @param now 今の日時
	 * @return ユーザー
	 */
	public User complete(TotpStep matchedStep, Instant now) {
		return User.register(userId, loginId, passwordHash, totpSecret, matchedStep, now);
	}

	public RegistrationId id() {
		return id;
	}

	public UserId userId() {
		return userId;
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

	public int otpFailureCount() {
		return otpFailureCount;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

}
