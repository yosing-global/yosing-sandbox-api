package net.yosing.sandbox.api.infrastructure.crypto;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * KMS の代わりに使うローカルの鍵（sandbox.local-crypto）。local のプロファイルだけで使う
 * （必須の確認は、鍵を使う設定クラスで行う）。
 *
 * @param totpSecretKey TOTP の秘密情報の暗号化用の AES の鍵（256 ビット、Base64）
 * @param jwtKey 仮認証 JWT の署名用の HMAC の鍵（256 ビット、Base64）
 */
@ConfigurationProperties("sandbox.local-crypto")
public record LocalCryptoProperties(@Nullable String totpSecretKey, @Nullable String jwtKey) {
}
