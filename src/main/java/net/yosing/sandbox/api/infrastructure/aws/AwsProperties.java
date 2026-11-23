package net.yosing.sandbox.api.infrastructure.aws;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AWS の SDK に共通の設定（sandbox.aws）。
 *
 * @param region リージョン（例：ap-northeast-1）
 */
@Validated
@ConfigurationProperties("sandbox.aws")
public record AwsProperties(@NotBlank String region) {
}
