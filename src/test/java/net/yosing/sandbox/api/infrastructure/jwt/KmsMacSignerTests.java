package net.yosing.sandbox.api.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.GenerateMacRequest;
import software.amazon.awssdk.services.kms.model.GenerateMacResponse;
import software.amazon.awssdk.services.kms.model.MacAlgorithmSpec;

/**
 * KMS の GenerateMac の呼び出し方（鍵の ARN・アルゴリズム・署名する内容）を確かめる。実際の KMS での動きは PR 環境で確かめる。
 */
class KmsMacSignerTests {

	private static final String KEY_ARN = "arn:aws:kms:ap-northeast-1:000000000000:key/test";

	private static final byte[] SIGNING_INPUT = "header.payload".getBytes(StandardCharsets.US_ASCII);

	private final KmsClient kms = mock(KmsClient.class);

	private final KmsMacSigner signer = new KmsMacSigner(kms, KEY_ARN);

	/**
	 * 鍵の ARN・HMAC_SHA_256・署名する内容を KMS に渡し、KMS の返した MAC を署名にする。
	 */
	@Test
	void signsWithKeyAndHmacSha256() throws JOSEException {
		when(kms.generateMac(any(GenerateMacRequest.class)))
			.thenReturn(GenerateMacResponse.builder().mac(SdkBytes.fromByteArray(new byte[] { 1, 2, 3 })).build());

		Base64URL signature = signer.sign(new JWSHeader(JWSAlgorithm.HS256), SIGNING_INPUT);

		ArgumentCaptor<GenerateMacRequest> request = ArgumentCaptor.forClass(GenerateMacRequest.class);
		verify(kms).generateMac(request.capture());
		assertThat(request.getValue().keyId()).isEqualTo(KEY_ARN);
		assertThat(request.getValue().macAlgorithm()).isEqualTo(MacAlgorithmSpec.HMAC_SHA_256);
		assertThat(request.getValue().message().asByteArray()).isEqualTo(SIGNING_INPUT);
		assertThat(signature.decode()).containsExactly(1, 2, 3);
	}

	/**
	 * HS256 以外のアルゴリズムでは署名せず、KMS も呼ばない。
	 */
	@Test
	void rejectsOtherAlgorithm() {
		assertThatExceptionOfType(JOSEException.class)
			.isThrownBy(() -> signer.sign(new JWSHeader(JWSAlgorithm.HS512), SIGNING_INPUT));
		verifyNoInteractions(kms);
	}

}
