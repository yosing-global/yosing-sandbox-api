package net.yosing.sandbox.api.application.cleanup;

/**
 * テストのユーザーの掃除の結果（件数だけ。ログイン ID 等の中身は持たない）。
 *
 * @param found 見つかったテストのユーザーの数
 * @param expired そのうち、残す期間を過ぎていた（消す対象の）数
 * @param deleted 実際に消した数（expired との差は、条件に合わず消さなかった数）
 */
public record CleanupTestUsersResult(int found, int expired, int deleted) {
}
