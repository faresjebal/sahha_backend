package com.sahha.patient.exception;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

import com.sahha.patient.config.RequestIdFilter;

@RestControllerAdvice
public class PatientProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(PatientProblemDetailsHandler.class);

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

	@ExceptionHandler(PatientAccessDeniedException.class)
	ResponseEntity<ProblemDetail> accessDenied(
			PatientAccessDeniedException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				problem(
						HttpStatus.FORBIDDEN,
						"Patient registry access denied",
						"The active organisation context does not permit this operation.",
						"urn:sahha:problem:patient-access-denied",
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

	@ExceptionHandler(PatientRegistrationNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			PatientRegistrationNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Patient registration not found",
						"The patient registration was not found in the active organisation.",
						"urn:sahha:problem:patient-registration-not-found",
						request));
	}

	@ExceptionHandler(PossibleDuplicateException.class)
	ResponseEntity<ProblemDetail> duplicateReviewRequired(
			PossibleDuplicateException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.CONFLICT,
				"Duplicate review required",
				"Possible patient matches must be reviewed before registration continues.",
				"urn:sahha:problem:patient-duplicate-review-required",
				request);
		problem.setProperty("duplicateCheck", exception.duplicateCheck());
		return response(HttpStatus.CONFLICT, problem);
	}

	@ExceptionHandler({
		PatientRegistrationConflictException.class,
		DataIntegrityViolationException.class
	})
	ResponseEntity<ProblemDetail> conflict(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Patient registration conflict",
						"The patient is already registered, the duplicate decision is invalid, or identifying information conflicts.",
						"urn:sahha:problem:patient-registration-conflict",
						request));
	}

	@ExceptionHandler(ConcurrentPatientModificationException.class)
	ResponseEntity<ProblemDetail> concurrentModification(
			ConcurrentPatientModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Patient registration changed",
						"The administrative record changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-patient-modification",
						request));
	}

	@ExceptionHandler(SharedPatientIdentityConflictException.class)
	ResponseEntity<ProblemDetail> sharedIdentityConflict(
			SharedPatientIdentityConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Shared patient identity requires review",
						"Stable identity fields cannot be changed from one organisation after the patient is linked elsewhere.",
						"urn:sahha:problem:shared-patient-identity-conflict",
						request));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ResponseEntity<ProblemDetail> invalidRequest(
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

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		String requestId = RequestIdFilter.requestId(request);
		LOGGER.error(
				"Unhandled Patient request failure requestId={} exception={}",
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
