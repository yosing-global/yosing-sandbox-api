package net.yosing.sandbox.api.application.port;

import java.nio.ByteBuffer;
import java.util.Arrays;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * テスト用の暗号化（フェイク）。暗号文は「userId（16バイト）＋秘密情報」で、復号では userId が同じかを確かめる
 * （本物と同じく、別のユーザーでは復号できない）。
 */
public class FakeTotpSecretEncryptor implements TotpSecretEncryptor {

	private static final int USER_ID_BYTES = 16;

	@Override
	public EncryptedTotpSecret encrypt(TotpSecret secret, UserId userId) {
		byte[] bytes = secret.bytes();
		return new EncryptedTotpSecret(
				ByteBuffer.allocate(USER_ID_BYTES + bytes.length).put(userIdBytes(userId)).put(bytes).array());
	}

	@Override
	public TotpSecret decrypt(EncryptedTotpSecret encrypted, UserId userId) {
		byte[] bytes = encrypted.bytes();
		if (!Arrays.equals(Arrays.copyOfRange(bytes, 0, USER_ID_BYTES), userIdBytes(userId))) {
			throw new IllegalStateException("別のユーザーの暗号文");
		}
		return new TotpSecret(Arrays.copyOfRange(bytes, USER_ID_BYTES, bytes.length));
	}

	private static byte[] userIdBytes(UserId userId) {
		return ByteBuffer.allocate(USER_ID_BYTES)
			.putLong(userId.value().getMostSignificantBits())
			.putLong(userId.value().getLeastSignificantBits())
			.array();
	}

}
