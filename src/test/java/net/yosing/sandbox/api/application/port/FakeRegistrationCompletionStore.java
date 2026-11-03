package net.yosing.sandbox.api.application.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.yosing.sandbox.api.domain.model.registration.Registration;
import net.yosing.sandbox.api.domain.model.session.Session;
import net.yosing.sandbox.api.domain.model.user.User;

/**
 * テスト用の登録の確定の保存（フェイク）。保存したものを覚え、決めた例外で失敗させることもできる。
 */
public class FakeRegistrationCompletionStore implements RegistrationCompletionStore {

	private final List<Completion> completions = new ArrayList<>();

	private Optional<RuntimeException> failure = Optional.empty();

	@Override
	public void complete(Registration registration, User user, Session session) {
		if (failure.isPresent()) {
			throw failure.get();
		}
		completions.add(new Completion(registration, user, session));
	}

	/**
	 * 次からの保存を、この例外で失敗させる（テストの準備）。
	 */
	public void failWith(RuntimeException exception) {
		failure = Optional.of(exception);
	}

	/**
	 * 保存したもの（テストの確認用）。
	 */
	public List<Completion> completions() {
		return List.copyOf(completions);
	}

	/**
	 * 保存した1回分。
	 *
	 * @param registration 確定した仮登録
	 * @param user 作ったユーザー
	 * @param session 作ったセッション
	 */
	public record Completion(Registration registration, User user, Session session) {
	}

}
