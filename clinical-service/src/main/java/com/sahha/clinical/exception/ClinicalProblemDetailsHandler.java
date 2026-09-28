package com.sahha.clinical.exception;

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

import com.sahha.clinical.config.RequestIdFilter;

@RestControllerAdvice
public class ClinicalProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(ClinicalProblemDetailsHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST, "Invalid consultation request",
				"One or more consultation fields are invalid.",
				"urn:sahha:problem:validation", request);
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			errors.putIfAbsent(fieldError.getField(),
					fieldError.getDefaultMessage() == null
							? "field is invalid" : fieldError.getDefaultMessage());
		}
		problem.setProperty("errors", errors);
		return response(HttpStatus.BAD_REQUEST, problem);
	}

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MethodArgumentTypeMismatchException.class,
		IllegalArgumentException.class
	})
	ResponseEntity<ProblemDetail> invalid(
			Exception exception,
			HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, problem(
				HttpStatus.BAD_REQUEST, "Invalid consultation request",
				"The consultation command is invalid.",
				"urn:sahha:problem:invalid-consultation-request", request));
	}

	@ExceptionHandler({
		ConsultationNotFoundException.class,
		ClinicalAppointmentNotFoundException.class
	})
	ResponseEntity<ProblemDetail> notFound(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, problem(
				HttpStatus.NOT_FOUND, "Clinical resource not found",
				"The requested consultation or eligible appointment was not found.",
				"urn:sahha:problem:clinical-resource-not-found", request));
	}

	@ExceptionHandler(ClinicalAccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(
			ClinicalAccessDeniedException exception,
			HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, problem(
				HttpStatus.FORBIDDEN, "Clinical access denied",
				"The active organisation and care relationship do not permit this operation.",
				"urn:sahha:problem:clinical-access-denied", request));
	}

	@ExceptionHandler(ConsultationStateConflictException.class)
	ResponseEntity<ProblemDetail> stateConflict(
			ConsultationStateConflictException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(
				HttpStatus.CONFLICT, "Consultation cannot be started",
				"A consultation draft requires an owned appointment in progress.",
				"urn:sahha:problem:consultation-state-conflict", request));
	}

	@ExceptionHandler(ConcurrentConsultationModificationException.class)
	ResponseEntity<ProblemDetail> concurrentModification(
			ConcurrentConsultationModificationException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(
				HttpStatus.CONFLICT, "Consultation draft changed",
				"The draft changed since it was loaded. Refresh and try again.",
				"urn:sahha:problem:concurrent-consultation-modification", request));
	}

	@ExceptionHandler(ConsultationIncompleteException.class)
	ResponseEntity<ProblemDetail> incomplete(
			ConsultationIncompleteException exception,
			HttpServletRequest request) {
		return response(HttpStatus.UNPROCESSABLE_ENTITY, problem(
				HttpStatus.UNPROCESSABLE_ENTITY, "Consultation is incomplete",
				"Add the required reason, symptom, examination, diagnosis, assessment, treatment, and follow-up before finalising.",
				"urn:sahha:problem:consultation-incomplete", request));
	}

	@ExceptionHandler({SchedulingContextUnavailableException.class, SharingContextUnavailableException.class, OrganisationContextUnavailableException.class})
	ResponseEntity<ProblemDetail> schedulingUnavailable(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, problem(
				HttpStatus.SERVICE_UNAVAILABLE, "Clinical access context unavailable",
				"Organisation, appointment or sharing eligibility could not be verified. Try again later.",
				"urn:sahha:problem:scheduling-context-unavailable", request));
	}

	@ExceptionHandler(AppointmentCompletionConflictException.class)
	ResponseEntity<ProblemDetail> appointmentCompletionConflict(
			AppointmentCompletionConflictException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.CONFLICT, "Appointment completion conflict",
				"The finalised consultation could not complete the appointment in its current state.",
				"urn:sahha:problem:appointment-completion-conflict", request);
		problem.setProperty("conflictCode", exception.getConflictCode());
		return response(HttpStatus.CONFLICT, problem);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error("Unhandled Clinical request failure requestId={} exception={}",
				RequestIdFilter.requestId(request),
				exception.getClass().getSimpleName());
		return response(HttpStatus.INTERNAL_SERVER_ERROR, problem(
				HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
				"The clinical request could not be completed.",
				"urn:sahha:problem:internal-error", request));
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
