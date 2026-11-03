package net.yosing.sandbox.api.application.session;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.user.LoginId;

/**
 * ログイン中のユーザーの情報（ダッシュボードの表示用）。
 *
 * @param loginId ログイン ID
 * @param createdAt 登録した日時
 */
public record CurrentUserResult(LoginId loginId, Instant createdAt) {
}
