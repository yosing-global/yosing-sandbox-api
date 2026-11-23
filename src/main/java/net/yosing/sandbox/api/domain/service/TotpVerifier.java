package net.yosing.sandbox.api.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import com.eatthepath.otp.HmacOneTimePasswordGenerator;
import net.yosing.sandbox.api.domain.model.shared.totp.OtpCode;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpParameters;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpStep;

/**
 * TOTP のコードの照合（RFC 6238）。登録の確定とログインの確定で使う。
 *
 * <p>今のステップの前後1ステップまで（端末の時計のずれと入力にかかる時間）を許し、一致したステップを返す。
 * 最後に使ったステップを渡したときは、それより後のステップだけを受け付ける（同じコードの再利用を防ぐ）。
 * コードの計算は java-otp（HOTP。ステップを数えるカウンターにする）を使う。java-otp は状態を持たないため、全部の要求で共有してよい。
 */
public final class TotpVerifier {

	/**
	 * 前後に許すステップの数。
	 */
	public static final int ALLOWED_STEP_DRIFT = 1;

	private static final String HMAC_ALGORITHM = "Hmac" + TotpParameters.ALGORITHM;

	// 桁数だけを指定すると HmacSHA1（TotpParameters.ALGORITHM と同じ）
	private final HmacOneTimePasswordGenerator generator = new HmacOneTimePasswordGenerator(TotpParameters.DIGITS);

	/**
	 * コードを照合する。
	 * @param secret 秘密情報
	 * @param code 入力されたコード
	 * @param step 今のステップ
	 * @param lastUsedStep 最後に使ったステップ（無ければ空）
	 * @return 一致したステップ。一致しなければ空
	 */
	public Optional<TotpStep> verify(TotpSecret secret, OtpCode code, TotpStep step, Optional<TotpStep> lastUsedStep) {
		byte[] input = code.value().getBytes(StandardCharsets.US_ASCII);
		Optional<TotpStep> matched = Optional.empty();
		// 一致したかで処理の時間が変わらないよう、途中で抜けずに全部の候補を計算する
		for (long offset = -ALLOWED_STEP_DRIFT; offset <= ALLOWED_STEP_DRIFT; offset++) {
			TotpStep candidate = step.plus(offset);
			boolean usable = lastUsedStep.map(candidate::isAfter).orElse(true);
			byte[] expected = generate(secret, candidate).value().getBytes(StandardCharsets.US_ASCII);
			if (MessageDigest.isEqual(expected, input) && usable && matched.isEmpty()) {
				matched = Optional.of(candidate);
			}
		}
		return matched;
	}

	/**
	 * ステップのコードを計算する（認証アプリが表示するものと同じ）。
	 * @param secret 秘密情報
	 * @param step ステップ
	 * @return コード
	 */
	public OtpCode generate(TotpSecret secret, TotpStep step) {
		try {
			return new OtpCode(this.generator.generateOneTimePasswordString(
					new SecretKeySpec(secret.bytes(), HMAC_ALGORITHM), step.value(), Locale.ROOT));
		}
		catch (InvalidKeyException ex) {
			throw new IllegalStateException("TOTP のコードを計算できない", ex);
		}
	}

}
