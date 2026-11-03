package net.yosing.sandbox.api.infrastructure.environment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 動いている環境（sandbox.environment）。AWS ではタスク定義の環境変数 APP_ENV・ROLE_ENV で渡す。
 *
 * @param appEnv アプリの環境（dev / test / pr&lt;番号&gt; / local 等）。仮認証 JWT の aud に使う（PR 環境ごとに分ける）
 * @param roleEnv タスクロール・プロファイルの単位（dev / test / pr / local 等）。KMS の暗号化コンテキストの env に使う
 *     （IAM の条件で、PR 環境はすべて pr に決まっている）
 */
@Validated
@ConfigurationProperties("sandbox.environment")
public record EnvironmentProperties(@NotBlank String appEnv, @NotBlank String roleEnv) {
}
