package net.yosing.sandbox.api.domain.model.registration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

	@Override
	public Optional<Registration> findById(RegistrationId id) {
		return Optional.ofNullable(registrations.get(id));
	}

	@Override
	public Optional<Registration> recordOtpFailure(RegistrationId id) {
		return findById(id).map(found -> {
			Registration failed = Registration.restore(found.id(), found.userId(), found.loginId(),
					found.passwordHash(), found.totpSecret(), found.otpFailureCount() + 1, found.createdAt(),
					found.expiresAt());
			registrations.put(id, failed);
			return failed;
		});
	}

	@Override
	public void delete(RegistrationId id) {
		registrations.remove(id);
	}

	/**
	 * 保存した仮登録（テストの確認用）。
	 */
	public List<Registration> all() {
		return new ArrayList<>(registrations.values());
	}

}
