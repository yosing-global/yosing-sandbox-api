package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.Map;

import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 項目のキー（PK / SK）。単一テーブルのため、キーの値に種類の接頭辞を付ける（Notion の 4.3 DB設計）。
 */
final class ItemKeys {

	static final String PK = "PK";

	static final String SK = "SK";

	private ItemKeys() {
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

	private static Map<String, AttributeValue> key(String pk, String sk) {
		return Map.of(PK, AttributeValue.fromS(pk), SK, AttributeValue.fromS(sk));
	}

}
