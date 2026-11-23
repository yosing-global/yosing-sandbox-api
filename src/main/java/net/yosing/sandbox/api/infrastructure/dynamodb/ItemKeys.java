package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.Map;

import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.session.SessionIdHash;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.UserId;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 項目のキー（PK / SK）。単一テーブルのため、キーの値に種類の接頭辞を付ける（Notion の 4.3 DB設計）。
 */
final class ItemKeys {

	static final String PK = "PK";

	static final String SK = "SK";

	/**
	 * 条件式で PK を指す名前（{@link #PK_NAMES} と一緒に使う）。
	 */
	static final String PK_REF = "#pk";

	/**
	 * 条件式の属性名の置き換え（{@code #pk} → {@code PK}）。
	 */
	static final Map<String, String> PK_NAMES = Map.of(PK_REF, PK);

	/**
	 * 項目が無いこと（新しく作るときの条件）
	 */
	static final String NOT_EXISTS = "attribute_not_exists(" + PK_REF + ")";

	/**
	 * 項目があること（更新・削除するときの条件）。
	 */
	static final String EXISTS = "attribute_exists(" + PK_REF + ")";

	private ItemKeys() {
	}

	/**
	 * ユーザーの項目（{@code USER#<userId>} / {@code PROFILE}）。
	 */
	static Map<String, AttributeValue> user(UserId id) {
		return key("USER#" + id, "PROFILE");
	}

	/**
	 * ログイン ID の項目（{@code LOGINID#<loginId>} / {@code LOGINID}）。ログイン ID の一意性と、ログインのときの検索に使う。
	 */
	static Map<String, AttributeValue> loginId(LoginId loginId) {
		return key("LOGINID#" + loginId.value(), "LOGINID");
	}

	/**
	 * 仮登録の項目（{@code REG#<registrationId>} / {@code REG}）。
	 */
	static Map<String, AttributeValue> registration(RegistrationId id) {
		return key("REG#" + id, "REG");
	}

	/**
	 * セッションの項目（{@code SESSION#<セッション ID のハッシュ>} / {@code SESSION}）。
	 */
	static Map<String, AttributeValue> session(SessionIdHash idHash) {
		return key("SESSION#" + idHash.value(), "SESSION");
	}

	private static Map<String, AttributeValue> key(String pk, String sk) {
		return Map.of(PK, AttributeValue.fromS(pk), SK, AttributeValue.fromS(sk));
	}

}
