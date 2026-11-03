package net.yosing.sandbox.api.domain.model.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

/**
 * セッションの発行（ID・ハッシュ・期限）を確かめる。
 */
class SessionTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00.789Z");

	/**
	 * セッションには ID のハッシュだけを持たせ、期限は発行（秒にそろえる）から30日後。
	 */
	@Test
	void issuesSessionWithHashedId() {
		UserId userId = UserId.generate();

		IssuedSession issued = Session.issue(userId, NOW);

		assertThat(issued.session().idHash()).isEqualTo(issued.sessionId().hash());
		assertThat(issued.session().userId()).isEqualTo(userId);
		assertThat(issued.session().createdAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
		assertThat(issued.session().lastAccessedAt()).isEqualTo(issued.session().createdAt());
		assertThat(issued.session().expiresAt()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
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

}
