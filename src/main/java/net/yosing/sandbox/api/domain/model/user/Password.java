package net.yosing.sandbox.api.domain.model.user;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 入力されたパスワード（平文）。保存せず、ハッシュにしてから保存する（{@link PasswordHash}）。
 *
 * <p>12〜64文字の、表示できる ASCII の文字（空白を含む）だけ。bcrypt は72バイトまでしか扱わないため、
 * 1文字が1バイトになる ASCII に限り、64文字でも72バイトを超えないようにする。
 * ログ等に出ないよう、toString では中身を出さない。
 */
public final class Password {

	/**
	 * 形式。入力の検証（presentation）でも使う。
	 */
	public static final String PATTERN = "[\\x20-\\x7E]{12,64}";

	private static final Pattern FORMAT = Pattern.compile(PATTERN);

	private final String value;

	/**
	 * @param value パスワード
	 * @throws IllegalArgumentException 形式が違う場合
	 */
	public Password(String value) {
		Objects.requireNonNull(value, "value");
		if (!FORMAT.matcher(value).matches()) {
			// 例外のメッセージにもパスワードを入れない
			throw new IllegalArgumentException("パスワードの形式が違う");
		}
		this.value = value;
	}

	/**
	 * @return パスワード（ハッシュにするときだけ使う）
	 */
	public String value() {
		return value;
	}

	@Override
	public String toString() {
		return "Password[****]";
	}

}
