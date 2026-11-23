package net.yosing.sandbox.api.infrastructure.jwt;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 仮認証 JWT の組み立てと検証を、ローカルの鍵（HmacSHA256）で確かめる。
 *
 * <p>正しい JWT だけを受け付け、環境・期限・鍵・改ざん・形式の違いを検出することを見る。
 */
class NimbusLoginTokenSignerTests {

	// HS256 の鍵（32バイト）
	private static final byte[] KEY = "dummy-hmac-key-0123456789abcdefg".getBytes(StandardCharsets.US_ASCII);

	private static final Instant ISSUED_AT = Instant.parse("2026-11-01T00:00:00Z");

	private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(300);

	private final NimbusLoginTokenSigner signer = signer(KEY, "dummy-pr12");

	private final LoginTokenClaims claims = new LoginTokenClaims(UserId.generate(), "dummy-token-id", ISSUED_AT,
			EXPIRES_AT);

	/**
	 * 署名した JWT を検証すると、同じ中身が取り出せる。
	 */
	@Test
	void verifiesWhatItSigned() {
		assertThat(this.signer.verify(this.signer.sign(this.claims), ISSUED_AT)).contains(this.claims);
	}

	/**
	 * JWT は HS256 で署名し、aud に環境名（app-env）を入れる。
	 */
	@Test
	void signsWithHs256AndAppEnvAudience() throws ParseException {
		SignedJWT jwt = SignedJWT.parse(this.signer.sign(this.claims));

		assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.HS256);
		assertThat(jwt.getJWTClaimsSet().getAudience()).isEqualTo(List.of("dummy-pr12"));
	}

	/**
	 * 別の環境（鍵が同じでも aud が違う）で発行した JWT は受け付けない。
	 */
	@Test
	void rejectsTokenForAnotherEnvironment() {
		String token = signer(KEY, "dummy-pr13").sign(this.claims);

		assertThat(this.signer.verify(token, ISSUED_AT)).isEmpty();
	}

	/**
	 * 有効期限の直前までは使え、有効期限の日時になったら使えない。
	 */
	@Test
	void rejectsExpiredToken() {
		String token = this.signer.sign(this.claims);

		assertThat(this.signer.verify(token, EXPIRES_AT.minusSeconds(1))).isPresent();
		assertThat(this.signer.verify(token, EXPIRES_AT)).isEmpty();
	}

	/**
	 * 別の鍵で署名した JWT は受け付けない。
	 */
	@Test
	void rejectsTokenSignedWithAnotherKey() {
		byte[] anotherKey = "dummy-another-key-0123456789abcd".getBytes(StandardCharsets.US_ASCII);
		String token = signer(anotherKey, "dummy-pr12").sign(this.claims);

		assertThat(this.signer.verify(token, ISSUED_AT)).isEmpty();
	}

	/**
	 * 中身（別のユーザー）を差し替えた JWT は、署名が合わず受け付けない。
	 */
	@Test
	void rejectsTamperedToken() {
		String[] original = this.signer.sign(this.claims).split("\\.");
		LoginTokenClaims anotherUser = new LoginTokenClaims(UserId.generate(), "dummy-token-id", ISSUED_AT, EXPIRES_AT);
		String[] another = this.signer.sign(anotherUser).split("\\.");
		String tampered = original[0] + "." + another[1] + "." + original[2];

		assertThat(this.signer.verify(tampered, ISSUED_AT)).isEmpty();
	}

	/**
	 * 署名の無い JWT（alg が none）は受け付けない。
	 */
	@Test
	void rejectsUnsignedToken() throws ParseException {
		String unsigned = new PlainJWT(SignedJWT.parse(this.signer.sign(this.claims)).getJWTClaimsSet()).serialize();

		assertThat(this.signer.verify(unsigned, ISSUED_AT)).isEmpty();
	}

	/**
	 * JWT の形式でないもの・長すぎるものは受け付けない（例外にしない）。
	 */
	@Test
	void rejectsMalformedOrTooLongToken() {
		assertThat(this.signer.verify("dummy-not-a-jwt", ISSUED_AT)).isEmpty();
		assertThat(this.signer.verify("a".repeat(1025), ISSUED_AT)).isEmpty();
	}

	private static NimbusLoginTokenSigner signer(byte[] key, String appEnv) {
		try {
			return new NimbusLoginTokenSigner(new MACSigner(key), new MACVerifier(key), appEnv);
		}
		catch (JOSEException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
