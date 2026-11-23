package net.yosing.sandbox.api.infrastructure.jwt;

import java.util.Set;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsInvalidMacException;
import software.amazon.awssdk.services.kms.model.MacAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyMacRequest;

/**
 * KMS の HMAC の鍵（VerifyMac）で JWT の署名を確かめる（HS256）。
 *
 * <p>署名が合わないときは false を返す。KMS に接続できない等のほかの失敗は、例外のまま返す（不正な JWT とは扱わない）。
 */
final class KmsMacVerifier implements JWSVerifier {

	private final KmsClient kms;

	private final String keyArn;

	KmsMacVerifier(KmsClient kms, String keyArn) {
		this.kms = kms;
		this.keyArn = keyArn;
	}

	@Override
	public boolean verify(JWSHeader header, byte[] signingInput, Base64URL signature) {
		if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())) {
			return false;
		}
		VerifyMacRequest request = VerifyMacRequest.builder()
			.keyId(keyArn)
			.macAlgorithm(MacAlgorithmSpec.HMAC_SHA_256)
			.message(SdkBytes.fromByteArray(signingInput))
			.mac(SdkBytes.fromByteArray(signature.decode()))
			.build();
		try {
			return Boolean.TRUE.equals(kms.verifyMac(request).macValid());
		}
		catch (KmsInvalidMacException ex) {
			// 署名が合わないとき、KMS は false ではなくこの例外を返す
			return false;
		}
	}

	@Override
	public Set<JWSAlgorithm> supportedJWSAlgorithms() {
		return Set.of(JWSAlgorithm.HS256);
	}

	// JCA（Java 標準の暗号）は使わない。Bean で共有するため、書き換えられるものを持たせず毎回作る
	@Override
	public JCAContext getJCAContext() {
		return new JCAContext();
	}

}
