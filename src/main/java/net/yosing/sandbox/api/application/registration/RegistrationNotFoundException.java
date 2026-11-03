package net.yosing.sandbox.api.application.registration;

/**
 * 仮登録が無い、期限切れ、または本登録・削除済み。API では 404 {@code REGISTRATION_NOT_FOUND} にする。
 */
public class RegistrationNotFoundException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public RegistrationNotFoundException() {
		super("仮登録が無い");
	}

}
