package net.yosing.sandbox.api.infrastructure.jwt;

import java.util.Set;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.jca.JCAContext;
import com.nimbusds.jose.util.Base64URL;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GenerateMacRequest;
import software.amazon.awssdk.services.kms.model.MacAlgorithmSpec;

/**
 * KMS の HMAC の鍵（GenerateMac）で JWT に署名する（HS256）。鍵はアプリに出てこない。
 */
final class KmsMacSigner implements JWSSigner {

	private final KmsClient kms;

	private final String keyArn;

	KmsMacSigner(KmsClient kms, String keyArn) {
		this.kms = kms;
		this.keyArn = keyArn;
	}

	@Override
	public Base64URL sign(JWSHeader header, byte[] signingInput) throws JOSEException {
		if (!JWSAlgorithm.HS256.equals(header.getAlgorithm())) {
			throw new JOSEException("HS256 にだけ対応する: " + header.getAlgorithm());
		}
		GenerateMacRequest request = GenerateMacRequest.builder()
			.keyId(keyArn)
			.macAlgorithm(MacAlgorithmSpec.HMAC_SHA_256)
			.message(SdkBytes.fromByteArray(signingInput))
			.build();
		return Base64URL.encode(kms.generateMac(request).mac().asByteArray());
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
