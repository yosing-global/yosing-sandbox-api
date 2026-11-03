package net.yosing.sandbox.api.infrastructure.crypto;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KMS の鍵（sandbox.kms）。aws のプロファイルだけで使う（必須の確認は {@link KmsCryptoConfiguration} で行う）。
 *
 * @param totpSecretKeyArn TOTP の秘密情報の暗号化用の鍵の ARN（タスク定義の環境変数 KMS_KEY_ARN）
 */
@ConfigurationProperties("sandbox.kms")
public record KmsProperties(@Nullable String totpSecretKeyArn) {
}
