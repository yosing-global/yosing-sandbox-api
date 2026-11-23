package net.yosing.sandbox.api.application.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import net.yosing.sandbox.api.domain.model.session.InMemorySessionRepository;
import net.yosing.sandbox.api.domain.model.session.IssuedSession;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import net.yosing.sandbox.api.domain.model.user.InMemoryUserRepository;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.PasswordHash;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * ログイン中のユーザーの情報の流れ（成功と期限の延長・セッションが無い・期限切れ・同時のログアウト・ユーザーが無い）を確かめる。
 */
class GetCurrentUserUseCaseTests {

	private static final Instant NOW = Instant.parse("2026-11-10T00:00:00Z");

	private final InMemorySessionRepository sessions = new InMemorySessionRepository();

	private final InMemoryUserRepository users = new InMemoryUserRepository();

	private final GetCurrentUserUseCase useCase = new GetCurrentUserUseCase(this.sessions, this.users,
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final User user = addUser();

	/**
	 * ログイン ID と登録した日時を返し、セッションの最終操作の日時を今にして期限を延ばす。延ばした後の期限も返す（保存した期限と同じ）。
	 */
	@Test
	void returnsUserAndExtendsSession() {
		IssuedSession issued = addSession(NOW.minus(Duration.ofHours(1)));

		CurrentUserResult result = this.useCase.get(issued.sessionId());

		assertThat(result.loginId()).isEqualTo(this.user.loginId());
		assertThat(result.createdAt()).isEqualTo(this.user.createdAt());
		assertThat(result.sessionExpiresAt()).isEqualTo(NOW.plus(Session.IDLE_TIMEOUT));
		assertThat(this.sessions.findByIdHash(issued.session().idHash())).hasValueSatisfying((saved) -> {
			assertThat(saved.lastAccessedAt()).isEqualTo(NOW);
			assertThat(saved.expiresAt()).isEqualTo(result.sessionExpiresAt());
		});
	}

	/**
	 * 発行から7日の上限に近いセッションは、返す期限も上限で止まる（12時間延ばした日時ではない）。
	 */
	@Test
	void returnsExpiryCappedAtAbsoluteTimeout() {
		// 上限まで6時間のセッション（最終操作は1時間前。最終操作から12時間より、発行から7日の方が先）
		Instant issuedAt = NOW.minus(Session.ABSOLUTE_TIMEOUT).plus(Duration.ofHours(6));
		IssuedSession issued = Session.issue(this.user.id(), issuedAt);
		this.sessions.add(Session.restore(issued.session().idHash(), this.user.id(), issued.session().createdAt(),
				NOW.minus(Duration.ofHours(1))));

		CurrentUserResult result = this.useCase.get(issued.sessionId());

		assertThat(result.sessionExpiresAt()).isEqualTo(issued.session().createdAt().plus(Session.ABSOLUTE_TIMEOUT));
	}

	/**
	 * 無いセッション・期限切れのセッションは、無効なセッションの失敗にする。
	 */
	@Test
	void rejectsMissingOrExpiredSession() {
		assertThatExceptionOfType(SessionInvalidException.class)
			.isThrownBy(() -> this.useCase.get(SessionId.generate()));

		IssuedSession expired = addSession(NOW.minus(Session.IDLE_TIMEOUT));
		assertThatExceptionOfType(SessionInvalidException.class)
			.isThrownBy(() -> this.useCase.get(expired.sessionId()));
	}

	/**
	 * 読んだ後に同時にログアウトされていたら（延長を保存できない）、無効なセッションの失敗にする（セッションを作り直さない）。
	 */
	@Test
	void rejectsSessionLoggedOutConcurrently() {
		IssuedSession issued = Session.issue(this.user.id(), NOW.minusSeconds(60));
		InMemorySessionRepository racing = new InMemorySessionRepository() {
			@Override
			public boolean saveAccess(Session touched) {
				delete(touched.idHash());
				return super.saveAccess(touched);
			}
		};
		racing.add(issued.session());
		GetCurrentUserUseCase racingUseCase = new GetCurrentUserUseCase(racing, this.users,
				Clock.fixed(NOW, ZoneOffset.UTC));

		assertThatExceptionOfType(SessionInvalidException.class)
			.isThrownBy(() -> racingUseCase.get(issued.sessionId()));
		assertThat(racing.findByIdHash(issued.session().idHash())).isEmpty();
	}

	/**
	 * セッションのユーザーが無ければ、無効なセッションの失敗にする。
	 */
	@Test
	void rejectsSessionOfMissingUser() {
		IssuedSession issued = Session.issue(UserId.generate(), NOW.minusSeconds(60));
		this.sessions.add(issued.session());

		assertThatExceptionOfType(SessionInvalidException.class).isThrownBy(() -> this.useCase.get(issued.sessionId()));
	}

	private IssuedSession addSession(Instant issuedAt) {
		IssuedSession issued = Session.issue(this.user.id(), issuedAt);
		this.sessions.add(issued.session());
		return issued;
	}

	private User addUser() {
		User registered = User.register(UserId.generate(), LoginId.of("dummy_alice"),
				new PasswordHash("{dummy}dummy-hash"), new EncryptedTotpSecret(new byte[] { 1 }), new TotpStep(123),
				Instant.parse("2026-10-01T00:00:00Z"));
		this.users.addUser(registered);
		return registered;
	}

}
