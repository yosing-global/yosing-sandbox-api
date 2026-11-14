package net.yosing.sandbox.api.application.session;

import java.time.Instant;

import net.yosing.sandbox.api.domain.model.user.LoginId;

/**
 * ログイン中のユーザーの情報（ダッシュボードの表示用）と、延ばした後のセッションの期限。
 *
 * @param loginId ログイン ID
 * @param createdAt 登録した日時
 * @param sessionExpiresAt 延ばした後のセッションの期限（BFF が Cookie の期限をそろえるために使う）
 */
public record CurrentUserResult(LoginId loginId, Instant createdAt, Instant sessionExpiresAt) {
}
