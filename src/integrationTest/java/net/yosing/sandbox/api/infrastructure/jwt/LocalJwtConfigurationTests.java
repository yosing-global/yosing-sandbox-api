package net.yosing.sandbox.api.infrastructure.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import net.yosing.sandbox.api.DynamoDbLocalContainerConfiguration;
import net.yosing.sandbox.api.application.port.LoginTokenClaims;
import net.yosing.sandbox.api.application.port.LoginTokenSigner;
import net.yosing.sandbox.api.domain.model.user.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * local のプロファイルで、ローカルの署名（application-local.yml の鍵の HmacSHA256）が使われることを確かめる。
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(DynamoDbLocalContainerConfiguration.class)
class LocalJwtConfigurationTests {

	@Autowired
	LoginTokenSigner signer;

	/**
	 * 署名の Bean が Nimbus の実装で、設定の鍵で署名・検証できる。
	 */
	@Test
	void usesLocalSigner() {
		Instant issuedAt = Instant.parse("2026-11-01T00:00:00Z");
		LoginTokenClaims claims = new LoginTokenClaims(UserId.generate(), "dummy-token-id", issuedAt,
				issuedAt.plusSeconds(300));

		assertThat(signer).isInstanceOf(NimbusLoginTokenSigner.class);
		assertThat(signer.verify(signer.sign(claims), issuedAt)).contains(claims);
	}

}
