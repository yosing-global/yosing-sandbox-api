package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.net.URI;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * DynamoDB の設定（sandbox.dynamodb）。
 *
 * @param tableName テーブルの名前（dev / test / stg / prod はタスク定義の環境変数 DYNAMODB_TABLE_NAME で渡す）
 * @param endpoint 接続先。DynamoDB Local を使うとき（dynamodb-local のプロファイル）だけ指定する
 */
@Validated
@ConfigurationProperties("sandbox.dynamodb")
public record DynamoDbProperties(@NotBlank String tableName, @Nullable URI endpoint) {
}
