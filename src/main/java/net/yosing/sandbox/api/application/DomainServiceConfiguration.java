package net.yosing.sandbox.api.application;

import net.yosing.sandbox.api.domain.service.TotpVerifier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ドメインサービス（domain.service）の Bean
 *
 * <p>domain は Spring に依存しない（@Service 等を付けない）ため、使う側の application で Bean にする。
 */
@Configuration(proxyBeanMethods = false)
class DomainServiceConfiguration {

	@Bean
	TotpVerifier totpVerifier() {
		return new TotpVerifier();
	}

}
