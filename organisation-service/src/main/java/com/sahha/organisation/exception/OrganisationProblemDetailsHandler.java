package com.sahha.organisation.exception;

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

import com.sahha.organisation.config.RequestIdFilter;

@RestControllerAdvice
public class OrganisationProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(OrganisationProblemDetailsHandler.class);

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

	@ExceptionHandler(OrganisationNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			OrganisationNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Organisation not found",
						"The requested organisation was not found.",
						"urn:sahha:problem:organisation-not-found",
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

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<ProblemDetail> conflict(
			DataIntegrityViolationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Organisation conflict",
						"An organisation with conflicting identifying information already exists.",
						"urn:sahha:problem:organisation-conflict",
						request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		String requestId = RequestIdFilter.requestId(request);
		LOGGER.error(
				"Unhandled Organisation request failure requestId={} exception={}",
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
		problem.setProperty(
				"requestId",
				RequestIdFilter.requestId(request));
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
