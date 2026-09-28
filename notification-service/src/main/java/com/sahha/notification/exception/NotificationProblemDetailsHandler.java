package com.sahha.notification.exception;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.sahha.notification.config.RequestIdFilter;

@RestControllerAdvice
public class NotificationProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(NotificationProblemDetailsHandler.class);

	@ExceptionHandler({
		ConstraintViolationException.class,
		HandlerMethodValidationException.class,
		MethodArgumentNotValidException.class,
		MethodArgumentTypeMismatchException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ProblemDetail> invalid(
			Exception exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.BAD_REQUEST,
				problem(
						HttpStatus.BAD_REQUEST,
						"Invalid notification request",
						"The notification identifier or pagination parameters are invalid.",
						"urn:sahha:problem:invalid-notification-request",
						request));
	}

	@ExceptionHandler(NotificationNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			NotificationNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Notification not found",
						"The notification or verified patient registration was not found for the signed-in account.",
						"urn:sahha:problem:notification-not-found",
						request));
	}

	@ExceptionHandler(NotificationAccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(
			NotificationAccessDeniedException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				problem(
						HttpStatus.FORBIDDEN,
						"Notification access denied",
						"The active organisation context does not permit this notification operation.",
						"urn:sahha:problem:notification-access-denied",
						request));
	}

	@ExceptionHandler(OrganisationContextUnavailableException.class)
	ResponseEntity<ProblemDetail> organisationUnavailable(
			OrganisationContextUnavailableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				problem(
						HttpStatus.SERVICE_UNAVAILABLE,
						"Organisation context unavailable",
						"Organisation access could not be verified. Try again later.",
						"urn:sahha:problem:organisation-context-unavailable",
						request));
	}

	@ExceptionHandler(com.sahha.notification.patient.PatientNotificationContextUnavailableException.class)
	ResponseEntity<ProblemDetail> patientUnavailable(
			com.sahha.notification.patient.PatientNotificationContextUnavailableException exception,
			HttpServletRequest request) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, problem(HttpStatus.SERVICE_UNAVAILABLE,
				"Patient context unavailable", "Patient ownership could not be verified. Try again later.",
				"urn:sahha:problem:patient-context-unavailable", request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error(
				"Unhandled Notification request failure requestId={} exception={}",
				RequestIdFilter.requestId(request),
				exception.getClass().getSimpleName());
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				problem(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Internal server error",
						"The notification request could not be completed.",
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
		problem.setProperty("requestId", RequestIdFilter.requestId(request));
		return problem;
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
