package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.session.SessionId;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * セッションの読み書き（項目の変換の往復、延長の条件付き更新、削除）を、DynamoDB Local で確かめる。
 *
 * <p>セッション ID はテストごとにランダムに作るため、テーブルを共有してもぶつからない。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class DynamoDbSessionRepositoryTests {

	private static final Instant NOW = Instant.parse("2026-11-01T00:00:00Z");

	@Autowired
	DynamoDbSessionRepository repository;

	@Autowired
	DynamoDbClient dynamoDb;

	@Autowired
	DynamoDbProperties properties;

	/**
	 * 保存したセッションを読むと、同じ値に戻る（項目の変換の往復）。無ければ空。
	 */
	@Test
	void findsSavedSession() {
		Session session = Session.issue(UserId.generate(), NOW).session();
		items().putSession(session);

		assertThat(repository.findByIdHash(session.idHash())).hasValueSatisfying(found -> {
			assertThat(found.userId()).isEqualTo(session.userId());
			assertThat(found.createdAt()).isEqualTo(session.createdAt());
			assertThat(found.lastAccessedAt()).isEqualTo(session.lastAccessedAt());
			assertThat(found.expiresAt()).isEqualTo(session.expiresAt());
		});
		assertThat(repository.findByIdHash(SessionId.generate().hash())).isEmpty();
	}

	/**
	 * 延ばしたセッションの最終操作の日時と期限（TTL の属性）を保存する。セッションが無ければ false で、項目を作らない。
	 */
	@Test
	void savesAccessOnlyForExistingSession() {
		Session session = Session.issue(UserId.generate(), NOW).session();
		items().putSession(session);
		Session touched = session.touch(NOW.plusSeconds(3600));

		assertThat(repository.saveAccess(touched)).isTrue();
		assertThat(items().sessionItem(session.idHash())).hasValueSatisfying(item -> {
			assertThat(item.get(SessionItemMapper.LAST_ACCESSED_AT).s()).isEqualTo(touched.lastAccessedAt().toString());
			assertThat(item.get(SessionItemMapper.EXPIRES_AT).n())
				.isEqualTo(Long.toString(touched.expiresAt().getEpochSecond()));
		});

		Session missing = Session.issue(UserId.generate(), NOW).session();
		assertThat(repository.saveAccess(missing.touch(NOW.plusSeconds(60)))).isFalse();
		assertThat(items().sessionItem(missing.idHash())).isEmpty();
	}

	/**
	 * 削除すると読めなくなる。無いものを削除しても失敗しない。
	 */
	@Test
	void deletesSession() {
		Session session = Session.issue(UserId.generate(), NOW).session();
		items().putSession(session);

		repository.delete(session.idHash());
		repository.delete(session.idHash());

		assertThat(repository.findByIdHash(session.idHash())).isEmpty();
	}

	private DynamoDbTestItems items() {
		return new DynamoDbTestItems(dynamoDb, properties);
	}

}
