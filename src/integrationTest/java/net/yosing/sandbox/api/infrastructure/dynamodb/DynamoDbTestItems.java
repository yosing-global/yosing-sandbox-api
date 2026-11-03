package net.yosing.sandbox.api.infrastructure.dynamodb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.registration.RegistrationId;
import net.yosing.sandbox.api.domain.model.session.SessionIdHash;
import net.yosing.sandbox.api.domain.model.user.LoginId;
import net.yosing.sandbox.api.domain.model.user.User;
import net.yosing.sandbox.api.domain.model.user.UserId;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * 結合テストで、DynamoDB Local の項目を直接準備・確認する部品。
 *
 * <p>キーの形と属性名は、本体の {@link ItemKeys}・各 ItemMapper を使う（テストに文字列で書かない）。
 * ほかのパッケージのテスト（API のテスト等）からも使えるよう public にしている。
 */
public final class DynamoDbTestItems {

	private final DynamoDbClient client;

	private final String tableName;

	public DynamoDbTestItems(DynamoDbClient client, DynamoDbProperties properties) {
		this.client = client;
		this.tableName = properties.tableName();
	}

	/**
	 * ログイン ID の項目を作る（本登録済みのログイン ID の準備）。
	 */
	public void putLoginId(LoginId loginId, UserId userId) {
		Map<String, AttributeValue> item = new HashMap<>(ItemKeys.loginId(loginId));
		item.put(UserItemMapper.USER_ID, AttributeValue.fromS(userId.toString()));
		client.putItem(request -> request.tableName(tableName).item(item));
	}

	/**
	 * ユーザーとログイン ID の項目を作る（本登録済みのユーザーの準備）。
	 */
	public void putUser(User user) {
		client.putItem(request -> request.tableName(tableName).item(UserItemMapper.toItem(user)));
		client.putItem(request -> request.tableName(tableName).item(UserItemMapper.toLoginIdItem(user)));
	}

	/**
	 * ユーザーを読む。
	 */
	public Optional<User> user(UserId id) {
		return get(ItemKeys.user(id)).map(UserItemMapper::fromItem);
	}

	/**
	 * 仮登録を読む。
	 */
	public Optional<Registration> registration(RegistrationId id) {
		return get(ItemKeys.registration(id)).map(RegistrationItemMapper::fromItem);
	}

	/**
	 * 仮登録の項目（属性そのもの）を読む。
	 */
	public Optional<Map<String, AttributeValue>> registrationItem(RegistrationId id) {
		return get(ItemKeys.registration(id));
	}

	/**
	 * ユーザーの項目を読む。
	 */
	public Optional<Map<String, AttributeValue>> userItem(UserId id) {
		return get(ItemKeys.user(id));
	}

	/**
	 * ログイン ID の項目を読む。
	 */
	public Optional<Map<String, AttributeValue>> loginIdItem(LoginId loginId) {
		return get(ItemKeys.loginId(loginId));
	}

	/**
	 * セッションの項目を読む。
	 */
	public Optional<Map<String, AttributeValue>> sessionItem(SessionIdHash idHash) {
		return get(ItemKeys.session(idHash));
	}

	private Optional<Map<String, AttributeValue>> get(Map<String, AttributeValue> key) {
		GetItemResponse response = client
			.getItem(request -> request.tableName(tableName).key(key).consistentRead(true));
		return response.hasItem() ? Optional.of(response.item()) : Optional.empty();
	}

}
