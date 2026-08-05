package com.sahha.auth.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.sahha.auth.config.RequestIdFilter;

@RestControllerAdvice
public class AuthProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AuthProblemDetailsHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Invalid request",
				"One or more request fields are invalid.",
				"urn:sahha:problem:validation",
				request);
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(
					fieldError.getField(),
					fieldError.getDefaultMessage() == null
							? "field is invalid"
							: fieldError.getDefaultMessage());
		}
		problem.setProperty("errors", errors);
		return response(HttpStatus.BAD_REQUEST, problem);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ProblemDetail> unreadable(
			HttpMessageNotReadableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				problem(
						HttpStatus.BAD_REQUEST,
						"Invalid request",
						"The request body is missing or malformed.",
						"urn:sahha:problem:malformed-request",
						request));
	}

	@ExceptionHandler(InvalidVerificationTokenException.class)
	ResponseEntity<ProblemDetail> invalidToken(
			InvalidVerificationTokenException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				problem(
						HttpStatus.BAD_REQUEST,
						"Invalid authentication token",
						"The authentication token is invalid or expired.",
						"urn:sahha:problem:invalid-auth-token",
						request));
	}

	@ExceptionHandler({
			InvalidAuthenticationException.class,
			InvalidRefreshTokenException.class
	})
	ResponseEntity<ProblemDetail> authenticationDenied(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.UNAUTHORIZED,
				problem(
						HttpStatus.UNAUTHORIZED,
						"Authentication required",
						"Authentication credentials are invalid or expired.",
						"urn:sahha:problem:authentication-required",
						request));
	}

	@ExceptionHandler(RateLimitExceededException.class)
	ResponseEntity<ProblemDetail> rateLimited(
			RateLimitExceededException exception,
			HttpServletRequest request) {
		long retryAfterSeconds = Math.max(
				1,
				exception.getRetryAfter().toSeconds());
		ProblemDetail problem = problem(
				HttpStatus.TOO_MANY_REQUESTS,
				"Too many authentication requests",
				"Too many requests were received. Try again later.",
				"urn:sahha:problem:authentication-rate-limit",
				request);
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(
						HttpHeaders.RETRY_AFTER,
						Long.toString(retryAfterSeconds))
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}

	@ExceptionHandler(OwnedSessionNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			OwnedSessionNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Resource not found",
						"The requested authentication resource was not found.",
						"urn:sahha:problem:auth-resource-not-found",
						request));
	}

	@ExceptionHandler(InvalidOrganisationContextException.class)
	ResponseEntity<ProblemDetail> invalidOrganisationContext(
			InvalidOrganisationContextException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Organisation context not found",
						"No eligible organisation context was found.",
						"urn:sahha:problem:organisation-context-not-found",
						request));
	}

	@ExceptionHandler(OrganisationContextDirectoryUnavailableException.class)
	ResponseEntity<ProblemDetail> organisationDirectoryUnavailable(
			OrganisationContextDirectoryUnavailableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				problem(
						HttpStatus.SERVICE_UNAVAILABLE,
						"Organisation directory unavailable",
						"The organisation context could not be verified. Try again later.",
						"urn:sahha:problem:organisation-directory-unavailable",
						request));
	}

	@ExceptionHandler(ForbiddenAccountOperationException.class)
	ResponseEntity<ProblemDetail> forbiddenOperation(
			ForbiddenAccountOperationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				problem(
						HttpStatus.FORBIDDEN,
						"Request forbidden",
						"The authenticated account cannot perform this operation.",
						"urn:sahha:problem:request-forbidden",
						request));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> invalidOperation(
			IllegalArgumentException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				problem(
						HttpStatus.BAD_REQUEST,
						"Invalid request",
						"The request could not be processed.",
						"urn:sahha:problem:invalid-request",
						request));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> conflict(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Request conflict",
						"The request conflicts with the current account state.",
						"urn:sahha:problem:account-conflict",
						request));
	}

	@ExceptionHandler(IllegalStateException.class)
	ResponseEntity<ProblemDetail> invalidState(
			IllegalStateException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Account state conflict",
						"The request cannot be completed in the current account state.",
						"urn:sahha:problem:account-state-conflict",
						request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		String requestId = requestId(request);
		LOGGER.error(
				"Unhandled Auth request failure requestId={} exception={}",
				requestId,
				exception.getClass().getSimpleName());
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				problem(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Internal server error",
						"The request could not be completed.",
						"urn:sahha:problem:internal-error",
						request));
	}

	private static ProblemDetail problem(
			HttpStatus status,
			String title,
			String detail,
			String type,
			HttpServletRequest request) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setType(URI.create(type));
		problem.setInstance(URI.create(request.getRequestURI()));
		problem.setProperty("requestId", requestId(request));
		return problem;
	}

	private static String requestId(HttpServletRequest request) {
		Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
		return requestId instanceof String value
				? value
				: "unavailable";
	}

	private static ResponseEntity<ProblemDetail> response(
			HttpStatus status,
			ProblemDetail problem) {
		return ResponseEntity.status(status)
				.cacheControl(CacheControl.noStore())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(problem);
	}
}
