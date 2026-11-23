package net.yosing.sandbox.api.application.port;

import net.yosing.sandbox.api.domain.model.shared.totp.EncryptedTotpSecret;
import net.yosing.sandbox.api.domain.model.shared.totp.TotpSecret;
import net.yosing.sandbox.api.domain.model.user.UserId;

/**
 * TOTP の秘密情報の暗号化・復号
 *
 * <p>暗号化コンテキストにユーザーと環境を含めるため、別のユーザー・別の環境の暗号文は復号できない。
 * 復号のときのユーザーは DB から読まず、処理中のユーザー（仮登録・仮認証 JWT のユーザー）を渡す。
 * 実装は infrastructure.crypto（aws：KMS、local：AES-GCM）。
 */
public interface TotpSecretEncryptor {

	/**
	 * 暗号化する。
	 * @param secret 秘密情報
	 * @param userId 秘密情報の持ち主
	 * @return 暗号文
	 */
	EncryptedTotpSecret encrypt(TotpSecret secret, UserId userId);

	/**
	 * 復号する。
	 * @param encrypted 暗号文
	 * @param userId 秘密情報の持ち主（暗号化したときと同じであること）
	 * @return 秘密情報
	 * @throws RuntimeException 復号できない場合（ユーザー・環境の違い、暗号文の改ざん等）
	 */
	TotpSecret decrypt(EncryptedTotpSecret encrypted, UserId userId);

}
