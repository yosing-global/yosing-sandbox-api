package net.yosing.sandbox.api.domain.model.session;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * ログインしているセッション。
 *
 * <p>期限は、最終操作から12時間、または発行から7日の早い方。認証済みの要求のたびに、最終操作の日時と期限を延ばす（{@link #touch}）。
 * 二要素認証で確かめ直す機会を減らしすぎないよう、短めにしている（一日の作業の中ではログインし直さず、週に一度は確かめ直す）。
 */
public final class Session {

	/**
	 * 最終操作からの有効期間。
	 */
	public static final Duration IDLE_TIMEOUT = Duration.ofHours(12);

	/**
	 * 発行からの有効期間（延ばしても、これより後にはならない）。
	 */
	public static final Duration ABSOLUTE_TIMEOUT = Duration.ofDays(7);

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

	/**
	 * 保存したセッションを戻す（DB から読んだとき）。期限は、発行と最終操作の日時から計算し直す。
	 *
	 * @param idHash セッション ID のハッシュ
	 * @param userId ログインしたユーザー
	 * @param createdAt 発行した日時
	 * @param lastAccessedAt 最終操作の日時
	 * @return セッション
	 */
	public static Session restore(SessionIdHash idHash, UserId userId, Instant createdAt, Instant lastAccessedAt) {
		return new Session(idHash, userId, createdAt, lastAccessedAt);
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
	 * 操作があった（認証済みの要求）。最終操作の日時を今にして、期限を延ばす（発行から7日より後にはならない）。
	 *
	 * @param now 今の日時（秒にそろえる）
	 * @return 延ばした後のセッション
	 * @throws IllegalStateException 期限が切れている場合
	 */
	public Session touch(Instant now) {
		if (isExpired(now)) {
			throw new IllegalStateException("期限が切れたセッションは延ばさない");
		}
		Instant accessedAt = now.truncatedTo(ChronoUnit.SECONDS);
		// 時計のずれ等で、最終操作の日時を前に戻さない
		return new Session(idHash, userId, createdAt, accessedAt.isAfter(lastAccessedAt) ? accessedAt : lastAccessedAt);
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
