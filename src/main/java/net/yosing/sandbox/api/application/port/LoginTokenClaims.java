package net.yosing.sandbox.api.application.port;

import java.time.Instant;
import java.util.Objects;

import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * 仮認証 JWT（loginToken）の中身
 *
 * <p>JWT の日時は秒の単位のため、署名して検証すると、日時の秒未満は切り捨てられる。
 *
 * @param userId 仮認証したユーザー（sub）
 * @param tokenId JWT の ID（jti）。USER に保存し、検証のときに照合する（1回だけ使えるようにする）
 * @param issuedAt 発行した日時（iat）
 * @param expiresAt 有効期限（exp）。この日時になったら使えない
 */
public record LoginTokenClaims(UserId userId, String tokenId, Instant issuedAt, Instant expiresAt) {

	public LoginTokenClaims {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(tokenId, "tokenId");
		Objects.requireNonNull(issuedAt, "issuedAt");
		Objects.requireNonNull(expiresAt, "expiresAt");
		if (tokenId.isBlank()) {
			throw new IllegalArgumentException("tokenId が空");
		}
		if (!expiresAt.isAfter(issuedAt)) {
			throw new IllegalArgumentException("expiresAt は issuedAt より後にする");
		}
	}

}
