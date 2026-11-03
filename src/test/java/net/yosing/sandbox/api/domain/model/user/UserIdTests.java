package net.yosing.sandbox.api.domain.model.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/**
 * ユーザー ID の生成と、文字列からの復元を確かめる。
 */
class UserIdTests {

	/**
	 * 生成するたびに別の ID になる。
	 */
	@Test
	void generatesDifferentIds() {
		assertThat(UserId.generate()).isNotEqualTo(UserId.generate());
	}

	/**
	 * 文字列にした ID を読み込むと、元の ID に戻る（DB への保存と読み込みの往復）。
	 */
	@Test
	void parsesWhatToStringReturns() {
		UserId id = UserId.generate();

		assertThat(UserId.parse(id.toString())).isEqualTo(id);
	}

	/**
	 * UUID の形でない文字列は受け付けない。
	 */
	@Test
	void rejectsNonUuid() {
		assertThatIllegalArgumentException().isThrownBy(() -> UserId.parse("not-a-uuid"));
	}

}
