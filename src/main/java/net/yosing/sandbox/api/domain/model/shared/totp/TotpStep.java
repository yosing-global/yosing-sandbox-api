package net.yosing.sandbox.api.domain.model.shared.totp;

import java.time.Instant;

/**
 * TOTP のステップ（Unix 時刻を30秒で割った値）。
 *
 * <p>コードが一致したステップをユーザーに保存し、それより後のステップだけを受け付けることで、同じコードの再利用を防ぐ。
 *
 * @param value ステップの番号
 */
public record TotpStep(long value) implements Comparable<TotpStep> {

	/**
	 * 日時が含まれるステップ。
	 *
	 * @param instant 日時
	 * @return ステップ
	 */
	public static TotpStep at(Instant instant) {
		return new TotpStep(Math.floorDiv(instant.getEpochSecond(), TotpParameters.PERIOD.toSeconds()));
	}

	/**
	 * @param steps 進めるステップの数（負なら戻す）
	 * @return 進めたステップ
	 */
	public TotpStep plus(long steps) {
		return new TotpStep(value + steps);
	}

	/**
	 * @param other 比べるステップ
	 * @return このステップが other より後なら true
	 */
	public boolean isAfter(TotpStep other) {
		return value > other.value;
	}

	@Override
	public int compareTo(TotpStep other) {
		return Long.compare(value, other.value);
	}

}
