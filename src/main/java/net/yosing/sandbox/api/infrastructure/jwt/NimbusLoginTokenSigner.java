package net.yosing.sandbox.api.infrastructure.jwt;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * 仮認証 JWT を Nimbus で組み立て・解析する（HS256）。
 *
 * <p>署名・検証の鍵は差し替える（aws：{@link KmsMacSigner}・{@link KmsMacVerifier}、local：Nimbus の MACSigner・MACVerifier）。
 * 検証では、形式・アルゴリズム・aud・有効期限を先に確かめ、通ったものだけ署名を確かめる（KMS の呼び出しを減らすため）。
 * 署名を確かめるまでは、中身を信用しない。
 */
final class NimbusLoginTokenSigner implements LoginTokenSigner {

	private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;

	// 発行する JWT は 300 文字程度。長すぎるものは解析しない（KMS の VerifyMac のメッセージの上限 4096 バイトも超えないようにする）
	private static final int MAX_TOKEN_LENGTH = 1024;

	private final JWSSigner signer;

	private final JWSVerifier verifier;

	private final String appEnv;

	NimbusLoginTokenSigner(JWSSigner signer, JWSVerifier verifier, String appEnv) {
		this.signer = signer;
		this.verifier = verifier;
		this.appEnv = appEnv;
	}

	@Override
	public String sign(LoginTokenClaims claims) {
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().audience(appEnv)
			.subject(claims.userId().toString())
			.jwtID(claims.tokenId())
			.issueTime(Date.from(claims.issuedAt()))
			.expirationTime(Date.from(claims.expiresAt()))
			.build();
		SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(ALGORITHM).type(JOSEObjectType.JWT).build(), claimsSet);
		try {
			jwt.sign(signer);
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("仮認証 JWT の署名に失敗した", ex);
		}
		return jwt.serialize();
	}

	@Override
	public Optional<LoginTokenClaims> verify(String token, Instant now) {
		if (token.length() > MAX_TOKEN_LENGTH) {
			return Optional.empty();
		}
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			JWSHeader header = jwt.getHeader();
			if (!ALGORITHM.equals(header.getAlgorithm()) || header.getCriticalParams() != null) {
				return Optional.empty();
			}
			Optional<LoginTokenClaims> claims = toClaims(jwt.getJWTClaimsSet(), now);
			if (claims.isEmpty() || !jwt.verify(verifier)) {
				return Optional.empty();
			}
			return claims;
		}
		catch (ParseException ex) {
			// JWT の形式でない（alg が none の JWT もここ）
			return Optional.empty();
		}
		catch (JOSEException ex) {
			throw new IllegalStateException("仮認証 JWT の検証に失敗した", ex);
		}
	}

	private Optional<LoginTokenClaims> toClaims(JWTClaimsSet claimsSet, Instant now) {
		// 他の環境（PR 環境どうしを含む）で発行した JWT は受け付けない
		if (!List.of(appEnv).equals(claimsSet.getAudience())) {
			return Optional.empty();
		}
		String subject = claimsSet.getSubject();
		String tokenId = claimsSet.getJWTID();
		Date issuedAt = claimsSet.getIssueTime();
		Date expiresAt = claimsSet.getExpirationTime();
		if (subject == null || tokenId == null || tokenId.isBlank() || issuedAt == null || expiresAt == null) {
			return Optional.empty();
		}
		if (!now.isBefore(expiresAt.toInstant()) || !expiresAt.after(issuedAt)) {
			return Optional.empty();
		}
		try {
			return Optional
				.of(new LoginTokenClaims(UserId.parse(subject), tokenId, issuedAt.toInstant(), expiresAt.toInstant()));
		}
		catch (IllegalArgumentException ex) {
			// sub が UUID でない
			return Optional.empty();
		}
	}

}
