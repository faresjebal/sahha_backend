package com.sahha.communication.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.sahha.communication.config.RequestIdFilter;

@RestControllerAdvice
public class CommunicationProblemDetailsHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(CommunicationProblemDetailsHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail detail = problem(HttpStatus.BAD_REQUEST, "Invalid communication request",
				"One or more communication fields are invalid.",
				"urn:sahha:problem:validation", request);
		Map<String,String> errors = new LinkedHashMap<>();
		for (FieldError error : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(error.getField(), error.getDefaultMessage() == null
					? "field is invalid" : error.getDefaultMessage());
		}
		detail.setProperty("errors", errors);
		return response(HttpStatus.BAD_REQUEST, detail);
	}

	@ExceptionHandler({HttpMessageNotReadableException.class,
			org.springframework.web.bind.MissingServletRequestParameterException.class,
			MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
	ResponseEntity<ProblemDetail> invalid(Exception exception, HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, problem(HttpStatus.BAD_REQUEST,
				"Invalid communication request", "The communication command is invalid.",
				"urn:sahha:problem:invalid-communication-request", request));
	}

	@ExceptionHandler(ConversationNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(ConversationNotFoundException exception,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, problem(HttpStatus.NOT_FOUND,
				"Conversation not found", "The requested conversation or eligible participant was not found.",
				"urn:sahha:problem:conversation-not-found", request));
	}

	@ExceptionHandler(ReferralNotFoundException.class)
	ResponseEntity<ProblemDetail> referralNotFound(ReferralNotFoundException exception,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, problem(HttpStatus.NOT_FOUND,
				"Referral not found",
				"The requested referral or eligible participant was not found.",
				"urn:sahha:problem:referral-not-found", request));
	}

	@ExceptionHandler(CommunicationAccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(CommunicationAccessDeniedException exception,
			HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, problem(HttpStatus.FORBIDDEN,
				"Communication access denied",
				"The active organisation or doctor relationship does not permit this operation.",
				"urn:sahha:problem:communication-access-denied", request));
	}

	@ExceptionHandler(ConversationConflictException.class)
	ResponseEntity<ProblemDetail> conflict(ConversationConflictException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(HttpStatus.CONFLICT,
				"Communication request conflict",
				"The request identifier was already used with different content.",
				"urn:sahha:problem:communication-request-conflict", request));
	}

	@ExceptionHandler({ReferralStateConflictException.class,
			ReferralVersionConflictException.class,
			ObjectOptimisticLockingFailureException.class})
	ResponseEntity<ProblemDetail> referralConflict(Exception exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(HttpStatus.CONFLICT,
				"Referral state conflict",
				"The referral changed or this status transition is not allowed.",
				"urn:sahha:problem:referral-state-conflict", request));
	}

	@ExceptionHandler(CommunicationContextUnavailableException.class)
	ResponseEntity<ProblemDetail> unavailable(CommunicationContextUnavailableException exception,
			HttpServletRequest request) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, problem(HttpStatus.SERVICE_UNAVAILABLE,
				"Communication context unavailable",
				"Doctor or patient eligibility could not be verified. Try again later.",
				"urn:sahha:problem:communication-context-unavailable", request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(Exception exception, HttpServletRequest request) {
		LOGGER.error("Unhandled Communication request failure requestId={} exception={}",
				RequestIdFilter.requestId(request), exception.getClass().getSimpleName());
		return response(HttpStatus.INTERNAL_SERVER_ERROR, problem(HttpStatus.INTERNAL_SERVER_ERROR,
				"Internal server error", "The communication request could not be completed.",
				"urn:sahha:problem:internal-error", request));
	}

	private static ProblemDetail problem(HttpStatus status, String title, String detail,
			String type, HttpServletRequest request) {
		ProblemDetail value = ProblemDetail.forStatusAndDetail(status, detail);
		value.setTitle(title); value.setType(URI.create(type));
		value.setInstance(URI.create(request.getRequestURI()));
		value.setProperty("requestId", RequestIdFilter.requestId(request));
		return value;
	}
	private static ResponseEntity<ProblemDetail> response(HttpStatus status, ProblemDetail body) {
		return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
				.contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}
}
