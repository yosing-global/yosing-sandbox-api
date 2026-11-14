package net.yosing.sandbox.api.domain.model.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import java.time.Instant;

import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * セッションの発行（ID・ハッシュ・期限）、クライアントの値の形、期限の判定と延長を確かめる。
 */
class SessionTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00.789Z");

	/**
	 * セッションには ID のハッシュだけを持たせ、期限は発行（秒にそろえる）から12時間後。
	 */
	@Test
	void issuesSessionWithHashedId() {
		UserId userId = UserId.generate();

		IssuedSession issued = Session.issue(userId, NOW);

		assertThat(issued.session().idHash()).isEqualTo(issued.sessionId().hash());
		assertThat(issued.session().userId()).isEqualTo(userId);
		assertThat(issued.session().createdAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(issued.session().lastAccessedAt()).isEqualTo(issued.session().createdAt());
		assertThat(issued.session().expiresAt()).isEqualTo(Instant.parse("2026-11-01T12:00:00Z"));
	}

	/**
	 * セッション ID は 256 ビットの乱数（Base64URL で43文字）で、毎回違う。
	 */
	@Test
	void generatesRandomSessionId() {
		SessionId id = SessionId.generate();

		assertThat(id.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
		assertThat(id).isNotEqualTo(SessionId.generate());
	}

	/**
	 * ハッシュは SHA-256 の16進数（同じ ID なら同じ値）。toString にセッション ID を出さない。
	 */
	@Test
	void hashesWithSha256() {
		SessionId id = new SessionId("dummy-session-id");

		assertThat(id.hash().value()).hasSize(64).isEqualTo(new SessionId("dummy-session-id").hash().value());
		assertThat(id.toString()).doesNotContain("dummy-session-id");
	}

	/**
	 * 発行したセッション（セッション ID とセッションの組）の toString にも、セッション ID を出さない。
	 */
	@Test
	void hidesSessionIdOfIssuedSession() {
		IssuedSession issued = Session.issue(UserId.generate(), NOW);

		assertThat(issued.toString()).doesNotContain(issued.sessionId().value());
	}

	/**
	 * クライアントの値は、発行する形（43文字の Base64URL）のときだけセッション ID にする。
	 */
	@Test
	void acceptsOnlyIssuedFormatFromClient() {
		String issued = SessionId.generate().value();

		assertThat(SessionId.fromClient(issued)).hasValueSatisfying(id -> assertThat(id.value()).isEqualTo(issued));
		assertThat(SessionId.fromClient(null)).isEmpty();
		assertThat(SessionId.fromClient("dummy-session-id")).isEmpty();
		assertThat(SessionId.fromClient(issued + "A")).isEmpty();
		assertThat(SessionId.fromClient(issued.substring(1) + "=")).isEmpty();
	}

	/**
	 * 期限の日時になったら期限切れ。
	 */
	@Test
	void expiresWhenExpiresAtComes() {
		Session session = Session.issue(UserId.generate(), NOW).session();

		assertThat(session.isExpired(session.expiresAt().minusSeconds(1))).isFalse();
		assertThat(session.isExpired(session.expiresAt())).isTrue();
	}

	/**
	 * 操作があると、最終操作の日時を今（秒にそろえる）にし、期限を今から12時間後に延ばす。
	 */
	@Test
	void extendsOnTouch() {
		Session session = Session.issue(UserId.generate(), NOW).session();

		Session touched = session.touch(Instant.parse("2026-11-01T08:00:00.500Z"));

		assertThat(touched.idHash()).isEqualTo(session.idHash());
		assertThat(touched.createdAt()).isEqualTo(session.createdAt());
		assertThat(touched.lastAccessedAt()).isEqualTo(Instant.parse("2026-11-01T08:00:00Z"));
		assertThat(touched.expiresAt()).isEqualTo(Instant.parse("2026-11-01T20:00:00Z"));
	}

	/**
	 * 12時間以内に操作を続けて延ばしても、期限は発行から7日（2026-11-08T00:00:00Z）より後にならない。
	 */
	@Test
	void capsExpiryAtAbsoluteTimeout() {
		Session touched = Session.issue(UserId.generate(), NOW).session();

		// 10時間ごとに操作を続ける（各回の操作は、その時点の期限＝前の操作から12時間より前）。最後は 2026-11-07T16:00:00Z
		for (int hours = 10; hours <= 160; hours += 10) {
			touched = touched.touch(Instant.parse("2026-11-01T00:00:00Z").plus(Duration.ofHours(hours)));
		}
		assertThat(touched.expiresAt()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));

		Session touchedAgain = touched.touch(Instant.parse("2026-11-07T22:00:00Z"));
		assertThat(touchedAgain.expiresAt()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));
		assertThat(touchedAgain.isExpired(Instant.parse("2026-11-08T00:00:00Z"))).isTrue();
	}

	/**
	 * 期限切れのセッションは延ばせない。
	 */
	@Test
	void rejectsTouchAfterExpiry() {
		Session session = Session.issue(UserId.generate(), NOW).session();

		assertThatIllegalStateException().isThrownBy(() -> session.touch(session.expiresAt()));
	}

	/**
	 * 最終操作より前の日時で操作があっても（時計のずれ）、最終操作の日時を前に戻さない。
	 */
	@Test
	void doesNotMoveLastAccessBackward() {
		Session touched = Session.issue(UserId.generate(), NOW).session().touch(Instant.parse("2026-11-01T10:00:00Z"));

		assertThat(touched.touch(Instant.parse("2026-11-01T09:00:00Z")).lastAccessedAt())
			.isEqualTo(Instant.parse("2026-11-01T10:00:00Z"));
	}

}
