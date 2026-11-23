package net.yosing.sandbox.api.domain.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOTP のコードの計算と照合（前後1ステップの許容、最後に使ったステップより後だけ）を確かめる。
 */
class TotpVerifierTests {

	// RFC 6238 の試験用の秘密情報（SHA1）
	private static final TotpSecret RFC_SECRET = new TotpSecret(
			"12345678901234567890".getBytes(StandardCharsets.US_ASCII));

	private final TotpVerifier verifier = new TotpVerifier();

	private final TotpSecret secret = new TotpSecret("dummy-totp-secret-20".getBytes(StandardCharsets.US_ASCII));

	private final TotpStep step = TotpStep.at(Instant.parse("2026-11-01T00:00:00Z"));

	/**
	 * RFC 6238 の試験用の値（8桁の下6桁）と同じコードを計算する。
	 */
	@Test
	void generatesRfcTestVectors() {
		assertThat(verifier.generate(RFC_SECRET, TotpStep.at(Instant.ofEpochSecond(59))))
			.isEqualTo(new OtpCode("287082"));
		assertThat(verifier.generate(RFC_SECRET, TotpStep.at(Instant.ofEpochSecond(1111111109L))))
			.isEqualTo(new OtpCode("081804"));
	}

	/**
	 * 今・1つ前・1つ後のステップのコードは受け付け、一致したステップを返す。
	 */
	@Test
	void acceptsAdjacentSteps() {
		for (long offset = -1; offset <= 1; offset++) {
			TotpStep candidate = step.plus(offset);

			assertThat(verifier.verify(secret, verifier.generate(secret, candidate), step, Optional.empty()))
				.contains(candidate);
		}
	}

	/**
	 * 2つ以上離れたステップのコードは受け付けない。
	 */
	@Test
	void rejectsDistantSteps() {
		assertThat(verifier.verify(secret, verifier.generate(secret, step.plus(-2)), step, Optional.empty())).isEmpty();
		assertThat(verifier.verify(secret, verifier.generate(secret, step.plus(2)), step, Optional.empty())).isEmpty();
	}

	/**
	 * 最後に使ったステップと、それより前のステップのコードは受け付けない（同じコードの再利用を防ぐ）。
	 */
	@Test
	void rejectsStepsNotAfterLastUsed() {
		OtpCode code = verifier.generate(secret, step);

		assertThat(verifier.verify(secret, code, step, Optional.of(step))).isEmpty();
		assertThat(verifier.verify(secret, code, step, Optional.of(step.plus(-1)))).contains(step);
	}

	/**
	 * 別の秘密情報のコードは受け付けない。
	 */
	@Test
	void rejectsCodeOfAnotherSecret() {
		TotpSecret another = new TotpSecret("dummy-another-secret".getBytes(StandardCharsets.US_ASCII));

		assertThat(verifier.verify(secret, verifier.generate(another, step), step, Optional.empty())).isEmpty();
	}

}
