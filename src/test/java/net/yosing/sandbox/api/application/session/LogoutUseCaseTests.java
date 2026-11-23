package net.yosing.sandbox.api.application.session;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.session.InMemorySessionRepository;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ログアウト（セッションの削除、何度呼んでも同じ結果）を確かめる。
 */
class LogoutUseCaseTests {

	private final InMemorySessionRepository sessions = new InMemorySessionRepository();

	private final LogoutUseCase useCase = new LogoutUseCase(sessions);

	/**
	 * セッションを削除する。もう一度呼んでも失敗しない。
	 */
	@Test
	void deletesSession() {
		IssuedSession issued = Session.issue(UserId.generate(), Instant.parse("2026-11-01T00:00:00Z"));
		sessions.add(issued.session());

		useCase.logout(issued.sessionId());
		useCase.logout(issued.sessionId());

		assertThat(sessions.findByIdHash(issued.session().idHash())).isEmpty();
	}

}
