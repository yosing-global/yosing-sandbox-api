package net.yosing.sandbox.api.application.port;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * テスト用の仮認証 JWT の署名（フェイク）。JWT の代わりに {@code dummy-token-<userId>-<jti>} を返し、中身を覚えておく。
 */
public class FakeLoginTokenSigner implements LoginTokenSigner {

	private final Map<String, LoginTokenClaims> issued = new HashMap<>();

	@Override
	public String sign(LoginTokenClaims claims) {
		// 同じ jti でもユーザーが違えば別のトークンにする
		String token = "dummy-token-" + claims.userId() + "-" + claims.tokenId();
		this.issued.put(token, claims);
		return token;
	}

	@Override
	public Optional<LoginTokenClaims> verify(String token, Instant now) {
		return Optional.ofNullable(this.issued.get(token)).filter((claims) -> now.isBefore(claims.expiresAt()));
	}

}
