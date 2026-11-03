package net.yosing.sandbox.api.domain.model.session;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * ログインしているセッション。
 *
 * <p>期限は、最終操作から30日、または発行から90日の早い方。認証済みの要求のたびに、最終操作の日時と期限を延ばす（セッションの API で作る）。
 */
public final class Session {

	/**
	 * 最終操作からの有効期間。
	 */
	public static final Duration IDLE_TIMEOUT = Duration.ofDays(30);

	/**
	 * 発行からの有効期間（延ばしても、これより後にはならない）。
	 */
	public static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(90);

	private final SessionIdHash idHash;

	private final UserId userId;

	private final Instant createdAt;

	private final Instant lastAccessedAt;

	private final Instant expiresAt;

	private Session(SessionIdHash idHash, UserId userId, Instant createdAt, Instant lastAccessedAt) {
		this.idHash = Objects.requireNonNull(idHash, "idHash");
		this.userId = Objects.requireNonNull(userId, "userId");
		this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
		this.lastAccessedAt = Objects.requireNonNull(lastAccessedAt, "lastAccessedAt");
		Instant idleExpiresAt = lastAccessedAt.plus(IDLE_TIMEOUT);
		Instant absoluteExpiresAt = createdAt.plus(ABSOLUTE_TIMEOUT);
		this.expiresAt = idleExpiresAt.isBefore(absoluteExpiresAt) ? idleExpiresAt : absoluteExpiresAt;
	}

	/**
	 * 新しいセッションを発行する。セッション ID はランダムに作り、セッションには、そのハッシュだけを持たせる。
	 *
	 * @param userId ログインしたユーザー
	 * @param now 今の日時（秒にそろえる）
	 * @return セッション ID（クライアントに返す元の値）とセッション
	 */
	public static IssuedSession issue(UserId userId, Instant now) {
		SessionId id = SessionId.generate();
		Instant createdAt = now.truncatedTo(ChronoUnit.SECONDS);
		return new IssuedSession(id, new Session(id.hash(), userId, createdAt, createdAt));
	}

	public SessionIdHash idHash() {
		return idHash;
	}

	public UserId userId() {
		return userId;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public Instant lastAccessedAt() {
		return lastAccessedAt;
	}

	public Instant expiresAt() {
		return expiresAt;
	}

}
