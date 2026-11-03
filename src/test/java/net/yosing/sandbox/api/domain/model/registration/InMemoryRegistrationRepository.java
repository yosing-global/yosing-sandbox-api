package net.yosing.sandbox.api.domain.model.registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * テスト用の、メモリ上の仮登録の保存先（フェイク）。
 */
public class InMemoryRegistrationRepository implements RegistrationRepository {

	private final Map<RegistrationId, Registration> registrations = new LinkedHashMap<>();

	@Override
	public void add(Registration registration) {
		if (registrations.putIfAbsent(registration.id(), registration) != null) {
			throw new IllegalStateException("同じ登録 ID の仮登録がある");
		}
	}

	/**
	 * 保存した仮登録（テストの確認用）。
	 */
	public List<Registration> all() {
		return new ArrayList<>(registrations.values());
	}

}
