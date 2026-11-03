package net.yosing.sandbox.api.domain.model.user;

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
 */
public final class User {

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
