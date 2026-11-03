package net.yosing.sandbox.api.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64URL;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.KmsException;
import software.amazon.awssdk.services.kms.model.KmsInvalidMacException;
import software.amazon.awssdk.services.kms.model.MacAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyMacRequest;
import software.amazon.awssdk.services.kms.model.VerifyMacResponse;

/**
 * KMS の VerifyMac の呼び出し方と、結果（合う・合わない・KMS の失敗）の扱いを確かめる。実際の KMS での動きは PR 環境で確かめる。
 */
class KmsMacVerifierTests {

	private static final String KEY_ARN = "arn:aws:kms:ap-northeast-1:000000000000:key/dummy";

	private static final byte[] SIGNING_INPUT = "dummy-header.dummy-payload".getBytes(StandardCharsets.US_ASCII);

	private static final JWSHeader HS256 = new JWSHeader(JWSAlgorithm.HS256);

	private static final Base64URL SIGNATURE = Base64URL.encode(new byte[] { 1, 2, 3 });

	private final KmsClient kms = mock(KmsClient.class);

	private final KmsMacVerifier verifier = new KmsMacVerifier(kms, KEY_ARN);

	/**
	 * 鍵の ARN・HMAC_SHA_256・署名した内容・署名を KMS に渡し、合っていれば true を返す。
	 */
	@Test
	void verifiesWithKeyAndHmacSha256() {
		when(kms.verifyMac(any(VerifyMacRequest.class))).thenReturn(VerifyMacResponse.builder().macValid(true).build());

		boolean valid = verifier.verify(HS256, SIGNING_INPUT, SIGNATURE);

		ArgumentCaptor<VerifyMacRequest> request = ArgumentCaptor.forClass(VerifyMacRequest.class);
		verify(kms).verifyMac(request.capture());
		assertThat(request.getValue().keyId()).isEqualTo(KEY_ARN);
		assertThat(request.getValue().macAlgorithm()).isEqualTo(MacAlgorithmSpec.HMAC_SHA_256);
		assertThat(request.getValue().message().asByteArray()).isEqualTo(SIGNING_INPUT);
		assertThat(request.getValue().mac().asByteArray()).containsExactly(1, 2, 3);
		assertThat(valid).isTrue();
	}

	/**
	 * 署名が合わないとき（KMS は例外を返す）は false を返す。
	 */
	@Test
	void returnsFalseForInvalidMac() {
		when(kms.verifyMac(any(VerifyMacRequest.class)))
			.thenThrow(KmsInvalidMacException.builder().message("dummy invalid mac").build());

		assertThat(verifier.verify(HS256, SIGNING_INPUT, SIGNATURE)).isFalse();
	}

	/**
	 * KMS のほかの失敗は、不正な JWT とは扱わず、例外のまま返す（API では 500 にする）。
	 */
	@Test
	void propagatesOtherKmsFailure() {
		when(kms.verifyMac(any(VerifyMacRequest.class))).thenThrow(KmsException.builder().message("dummy failure").build());

		assertThatExceptionOfType(KmsException.class)
			.isThrownBy(() -> verifier.verify(HS256, SIGNING_INPUT, SIGNATURE));
	}

	/**
	 * HS256 以外のアルゴリズムは false を返し、KMS も呼ばない。
	 */
	@Test
	void rejectsOtherAlgorithm() {
		assertThat(verifier.verify(new JWSHeader(JWSAlgorithm.HS512), SIGNING_INPUT, SIGNATURE)).isFalse();
		verifyNoInteractions(kms);
	}

}
