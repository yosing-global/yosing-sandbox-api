package net.yosing.sandbox.api.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * E2E（yosing-sandbox-e2e）が作ったテストのユーザーを探す・消す（テストのユーザーの掃除だけで使う）。
 *
 * <p>テストの都合のため、ドメインの UserRepository には足さない。実装は infrastructure.dynamodb。
 * 消すのはログイン ID が {@link #TEST_LOGIN_ID_PREFIX} で始まるユーザーだけ（実装でも条件にする）。
 */
public interface TestUserCleanupStore {

	/**
	 * E2E のテストのユーザーのログイン ID の先頭（E2E の設計で決めた形：{@code e2e_dummy_} ＋ 時刻 ＋ 乱数）。
	 */
	String TEST_LOGIN_ID_PREFIX = "e2e_dummy_";

	/**
	 * ログイン ID が {@code loginIdPrefix} で始まるユーザーを、すべて探す。
	 *
	 * @param loginIdPrefix ログイン ID の先頭（{@link #TEST_LOGIN_ID_PREFIX} で始まること）
	 * @return 見つかったユーザー
	 * @throws IllegalArgumentException 先頭が {@link #TEST_LOGIN_ID_PREFIX} で始まらない場合
	 */
	List<TestUser> findByLoginIdPrefix(String loginIdPrefix);

	/**
	 * ユーザー（ユーザーの項目とログイン ID の項目）を消す。両方を消すか、何もしない。
	 *
	 * <p>ユーザーのログイン ID が {@code user} と同じで {@link #TEST_LOGIN_ID_PREFIX} で始まること、ログイン ID の項目が
	 * このユーザーを指していることを条件にする。
	 *
	 * @param user 消すユーザー（{@link #findByLoginIdPrefix} の結果）
	 * @return 消したら true。条件に合わなければ false（既に消えた等）
	 * @throws IllegalArgumentException ログイン ID が {@link #TEST_LOGIN_ID_PREFIX} で始まらない場合
	 */
	boolean delete(TestUser user);

	/**
	 * 掃除の対象を決めるための、ユーザーの最小限の情報。
	 *
	 * @param id ユーザーの ID
	 * @param loginId ログイン ID
	 * @param createdAt 登録した日時
	 */
	record TestUser(UserId id, LoginId loginId, Instant createdAt) {

		public TestUser {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(loginId, "loginId");
			Objects.requireNonNull(createdAt, "createdAt");
		}

	}

}
