package net.yosing.sandbox.api.application.registration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 認証アプリの設定 URI の設定（sandbox.otpauth）
 *
 * @param issuer 発行者（認証アプリに表示されるサービスの名前）。環境ごとに見分けられるよう、環境名を付ける（application.yml）
 */
@Validated
@ConfigurationProperties("sandbox.otpauth")
public record OtpauthProperties(@NotBlank String issuer) {
}
