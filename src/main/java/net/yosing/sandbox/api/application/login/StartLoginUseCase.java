package net.yosing.sandbox.api.application.login;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.application.port.PasswordHasher;
import net.yosing.sandbox.api.domain.model.user.Password;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserRepository;

import org.springframework.stereotype.Service;

/**
 * ログインの開始（POST /v1/logins）
 *
 * <ol>
 * <li>ログイン ID でユーザーを取り出す。無ければ 401</li>
 * <li>ロック中（パスワード・OTP のどちらでロックしたかは区別しない）なら、照合せずに 401（ふつうの失敗と同じ応答）</li>
 * <li>照合の前に、試行を失敗として数えて保存する（5回目ならロックの期限も）。同時のほかの試行に先を越されたら 401</li>
 * <li>パスワードを照合する。違えば 401（失敗は数え済み）</li>
 * <li>合えば、パスワードの失敗回数を0に戻してロックを解き（OTP の失敗回数は戻さない）、新しい jti を保存して、仮認証 JWT（5分）を返す</li>
 * </ol>
 *
 * <p>照合しないで 401 を返すとき（ユーザーが無い・ロック中・先を越された）も、照合と同じくらいの時間をかける
 * （応答時間で、ユーザーの有無やロックが分からないようにする）。
 */
@Service
public class StartLoginUseCase {

	/**
	 * 仮認証 JWT の有効期間
	 */
	public static final Duration LOGIN_TOKEN_VALIDITY = Duration.ofMinutes(5);

	private final UserRepository users;

	private final PasswordHasher passwordHasher;

	private final LoginTokenSigner loginTokenSigner;

	private final Clock clock;

	// 照合しないときに、時間をかけるために照合する相手（どのパスワードの結果も使わない）
	private final PasswordHash timingHash;

	public StartLoginUseCase(UserRepository users, PasswordHasher passwordHasher, LoginTokenSigner loginTokenSigner,
			Clock clock) {
		this.users = users;
		this.passwordHasher = passwordHasher;
		this.loginTokenSigner = loginTokenSigner;
		this.clock = clock;
		this.timingHash = passwordHasher.hash(new Password("timing-equalizer-" + UUID.randomUUID()));
	}

	/**
	 * ログインを始める。
	 * @param command 入力
	 * @return 仮認証 JWT と期限
	 * @throws InvalidCredentialsException ログイン ID かパスワードが違う、またはロック中の場合
	 */
	public StartLoginResult start(StartLoginCommand command) {
		Instant now = this.clock.instant();
		Optional<User> found = this.users.findByLoginId(command.loginId());
		if (found.isEmpty() || found.get().isLocked(now)) {
			throw rejectWithoutVerifying(command.password());
		}
		User user = found.get();

		User counted = user.countPasswordAttempt(now);
		if (!this.users.savePasswordAttempt(user, counted)) {
			throw rejectWithoutVerifying(command.password());
		}
		if (!this.passwordHasher.matches(command.password(), user.passwordHash())) {
			throw new InvalidCredentialsException();
		}

		String tokenId = UUID.randomUUID().toString();
		this.users.savePasswordVerified(counted.passwordVerified(tokenId));
		// JWT の日時は秒の単位のため、そろえておく
		Instant issuedAt = now.truncatedTo(ChronoUnit.SECONDS);
		Instant expiresAt = issuedAt.plus(LOGIN_TOKEN_VALIDITY);
		String token = this.loginTokenSigner.sign(new LoginTokenClaims(user.id(), tokenId, issuedAt, expiresAt));
		return new StartLoginResult(token, expiresAt);
	}

	private InvalidCredentialsException rejectWithoutVerifying(Password password) {
		this.passwordHasher.matches(password, this.timingHash);
		return new InvalidCredentialsException();
	}

}
