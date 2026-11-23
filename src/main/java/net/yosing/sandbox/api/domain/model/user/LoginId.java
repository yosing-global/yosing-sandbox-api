package net.yosing.sandbox.api.domain.model.user;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * ログイン ID。3〜32文字の英小文字・数字・{@code _}・{@code -}。
 *
 * <p>大文字は小文字にそろえてから検証・保存する（{@link #of(String)}）。
 *
 * @param value ログイン ID（小文字にそろえた値）
 */
public record LoginId(String value) {

	/**
	 * 形式（小文字にそろえた後）。入力の検証（presentation）では、大文字も受け付けるよう、大文字と小文字を区別せずに使う。
	 */
	public static final String PATTERN = "[a-z0-9_-]{3,32}";

	private static final Pattern NORMALIZED = Pattern.compile(PATTERN);

	// CASE_INSENSITIVE だけ（UNICODE_CASE なし）では、ASCII の大文字・小文字だけを同じに扱う
	private static final Pattern INPUT = Pattern.compile(PATTERN, Pattern.CASE_INSENSITIVE);

	public LoginId {
		Objects.requireNonNull(value, "value");
		if (!NORMALIZED.matcher(value).matches()) {
			throw new IllegalArgumentException("ログイン ID の形式が違う");
		}
	}

	/**
	 * 入力されたログイン ID を、小文字にそろえて作る。
	 * @param input 入力されたログイン ID（大文字を含んでよい）
	 * @return ログイン ID
	 * @throws IllegalArgumentException 形式が違う場合
	 */
	public static LoginId of(String input) {
		Objects.requireNonNull(input, "input");
		if (!INPUT.matcher(input).matches()) {
			throw new IllegalArgumentException("ログイン ID の形式が違う");
		}
		return new LoginId(input.toLowerCase(Locale.ROOT));
	}

	@Override
	public String toString() {
		return this.value;
	}

}
