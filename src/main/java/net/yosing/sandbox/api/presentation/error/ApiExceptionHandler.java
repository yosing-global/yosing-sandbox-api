package net.yosing.sandbox.api.presentation.error;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import net.yosing.sandbox.api.application.error.OtpAttemptsExceededException;
import net.yosing.sandbox.api.application.error.OtpInvalidException;
import net.yosing.sandbox.api.application.login.InvalidCredentialsException;
import net.yosing.sandbox.api.application.login.LoginTokenInvalidException;
import net.yosing.sandbox.api.application.registration.LoginIdTakenException;
import net.yosing.sandbox.api.application.registration.RegistrationNotFoundException;
import net.yosing.sandbox.api.application.session.SessionInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 例外を、エラー応答（Problem Details（RFC 9457）に {@code code} を加えたもの）に変える。
 *
 * <ul>
 * <li>入力の誤り（検証・JSON の形式）：400 {@code VALIDATION_FAILED}。{@code errors} に項目ごとの誤りを入れる（入力の値は入れない）</li>
 * <li>アプリの例外：それぞれの {@link ErrorCode}</li>
 * <li>想定外の例外：500 {@code INTERNAL_ERROR}（内容は応答に出さず、ログに出す）</li>
 * <li>Spring MVC の例外（404・405 等）：Spring の既定の Problem Details（{@code code} なし。BFF は呼ばない）</li>
 * </ul>
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// 同じ入力に同じ応答を返すよう、並び順を決める
		List<FieldErrorResponse> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> new FieldErrorResponse(error.getField(), Objects.toString(error.getDefaultMessage(), "")))
			.sorted(Comparator.comparing(FieldErrorResponse::field).thenComparing(FieldErrorResponse::message))
			.toList();
		return validationFailed(errors);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// JSON の形式の誤り・型の違い等
		return validationFailed(List.of());
	}

	@ExceptionHandler(LoginIdTakenException.class)
	ResponseEntity<Object> handleLoginIdTaken(LoginIdTakenException ex) {
		return response(problem(ErrorCode.LOGIN_ID_TAKEN));
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ResponseEntity<Object> handleInvalidCredentials(InvalidCredentialsException ex) {
		return response(problem(ErrorCode.INVALID_CREDENTIALS));
	}

	@ExceptionHandler(LoginTokenInvalidException.class)
	ResponseEntity<Object> handleLoginTokenInvalid(LoginTokenInvalidException ex) {
		return response(problem(ErrorCode.LOGIN_TOKEN_INVALID));
	}

	@ExceptionHandler(SessionInvalidException.class)
	ResponseEntity<Object> handleSessionInvalid(SessionInvalidException ex) {
		// Bearer の認証が要ることを示す（RFC 6750）
		ProblemDetail problem = problem(ErrorCode.SESSION_INVALID);
		return ResponseEntity.status(problem.getStatus()).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(problem);
	}

	@ExceptionHandler(RegistrationNotFoundException.class)
	ResponseEntity<Object> handleRegistrationNotFound(RegistrationNotFoundException ex) {
		return response(problem(ErrorCode.REGISTRATION_NOT_FOUND));
	}

	@ExceptionHandler(OtpInvalidException.class)
	ResponseEntity<Object> handleOtpInvalid(OtpInvalidException ex) {
		ProblemDetail problem = problem(ErrorCode.OTP_INVALID);
		problem.setProperty("remainingAttempts", ex.remainingAttempts());
		return response(problem);
	}

	@ExceptionHandler(OtpAttemptsExceededException.class)
	ResponseEntity<Object> handleOtpAttemptsExceeded(OtpAttemptsExceededException ex) {
		return response(problem(ErrorCode.OTP_ATTEMPTS_EXCEEDED));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex) {
		LOGGER.error("想定外のエラー", ex);
		return response(problem(ErrorCode.INTERNAL_ERROR));
	}

	private static ResponseEntity<Object> validationFailed(List<FieldErrorResponse> errors) {
		ProblemDetail problem = problem(ErrorCode.VALIDATION_FAILED);
		problem.setProperty("errors", errors);
		return response(problem);
	}

	private static ProblemDetail problem(ErrorCode code) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), code.detail());
		problem.setProperty("code", code.name());
		return problem;
	}

	private static ResponseEntity<Object> response(ProblemDetail problem) {
		return ResponseEntity.status(problem.getStatus()).body(problem);
	}

	/**
	 * 入力の誤り（項目ごと）。
	 *
	 * @param field 項目名
	 * @param message 誤りの内容
	 */
	record FieldErrorResponse(String field, String message) {
	}

}
