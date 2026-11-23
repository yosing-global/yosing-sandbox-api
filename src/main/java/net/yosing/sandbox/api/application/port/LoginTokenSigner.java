package net.yosing.sandbox.api.application.port;

import java.time.Instant;
import java.util.Optional;

/**
 * 仮認証 JWT（loginToken）の署名・検証
 *
 * <p>JWT の aud には環境名（app-env）を入れ、検証では自分の環境の JWT だけを受け付ける（署名の鍵は環境で共有するため）。
 * 有効期間（5分）や jti の採番はユースケースが決め、ここでは JWT にするだけ。jti と USER の値の照合もユースケースで行う。
 * 実装は infrastructure.jwt（aws：KMS の HMAC、local：HmacSHA256）。
 */
public interface LoginTokenSigner {

	/**
	 * 署名した JWT を作る。
	 * @param claims JWT の中身
	 * @return JWT（コンパクトな形式の文字列）
	 */
	String sign(LoginTokenClaims claims);

	/**
	 * JWT を検証し、中身を取り出す。
	 * @param token クライアントから受け取った JWT
	 * @param now 今の日時（有効期限の判定に使う）
	 * @return 中身。署名・形式・aud・有効期限のどれかが正しくなければ空
	 */
	Optional<LoginTokenClaims> verify(String token, Instant now);

}
