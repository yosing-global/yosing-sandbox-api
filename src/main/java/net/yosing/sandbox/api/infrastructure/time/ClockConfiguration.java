package net.yosing.sandbox.api.infrastructure.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 時計。ユースケースは今の日時をこの Bean（java.time.Clock）から取る。
 *
 * <p>java.time.Clock は Java 標準の差し替えられる時計のため、port のインターフェースは作らない。
 * テストでは Clock.fixed で決まった日時の時計に差し替える。
 */
@Configuration(proxyBeanMethods = false)
class ClockConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
