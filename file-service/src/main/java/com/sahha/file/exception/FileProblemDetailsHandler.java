package com.sahha.file.exception;

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

import com.sahha.file.config.RequestIdFilter;

@RestControllerAdvice
public class FileProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(FileProblemDetailsHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST, "Invalid file request",
				"One or more file fields are invalid.",
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
		IllegalArgumentException.class,
		InvalidFileUploadException.class
	})
	ResponseEntity<ProblemDetail> invalid(
			Exception exception,
			HttpServletRequest request) {
		return response(HttpStatus.BAD_REQUEST, problem(
				HttpStatus.BAD_REQUEST, "Invalid file request",
				"The medical-file request is invalid.",
				"urn:sahha:problem:invalid-file-request", request));
	}

	@ExceptionHandler(FileResourceNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			FileResourceNotFoundException exception,
			HttpServletRequest request) {
		return response(HttpStatus.NOT_FOUND, problem(
				HttpStatus.NOT_FOUND, "File resource not found",
				"The requested file resource or eligible consultation was not found.",
				"urn:sahha:problem:file-resource-not-found", request));
	}

	@ExceptionHandler(FileAccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(
			FileAccessDeniedException exception,
			HttpServletRequest request) {
		return response(HttpStatus.FORBIDDEN, problem(
				HttpStatus.FORBIDDEN, "File access denied",
				"The active organisation and clinical relationship do not permit this operation.",
				"urn:sahha:problem:file-access-denied", request));
	}

	@ExceptionHandler(FileUploadConflictException.class)
	ResponseEntity<ProblemDetail> conflict(
			FileUploadConflictException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(
				HttpStatus.CONFLICT, "File upload is no longer available",
				"The upload ticket is expired, consumed, or conflicts with the current file state.",
				"urn:sahha:problem:file-upload-conflict", request));
	}

	@ExceptionHandler(FileScanConflictException.class)
	ResponseEntity<ProblemDetail> scanConflict(
			FileScanConflictException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(
				HttpStatus.CONFLICT, "File scan decision conflicts with its state",
				"Only a stored file with a pending scan can accept this decision.",
				"urn:sahha:problem:file-scan-conflict", request));
	}

	@ExceptionHandler(FileDownloadConflictException.class)
	ResponseEntity<ProblemDetail> downloadConflict(
			FileDownloadConflictException exception,
			HttpServletRequest request) {
		return response(HttpStatus.CONFLICT, problem(
				HttpStatus.CONFLICT, "File download is no longer available",
				"The download grant was consumed or the file is not available for download.",
				"urn:sahha:problem:file-download-conflict", request));
	}

	@ExceptionHandler(FileDownloadGrantExpiredException.class)
	ResponseEntity<ProblemDetail> downloadExpired(
			FileDownloadGrantExpiredException exception,
			HttpServletRequest request) {
		return response(HttpStatus.GONE, problem(
				HttpStatus.GONE, "File download grant expired",
				"The short-lived download grant has expired. Request a new grant.",
				"urn:sahha:problem:file-download-grant-expired", request));
	}

	@ExceptionHandler({
		ClinicalContextUnavailableException.class,
		SharingContextUnavailableException.class,
		FileStorageUnavailableException.class
	})
	ResponseEntity<ProblemDetail> unavailable(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(HttpStatus.SERVICE_UNAVAILABLE, problem(
				HttpStatus.SERVICE_UNAVAILABLE, "File dependency unavailable",
				"File eligibility or private storage could not be verified. Try again later.",
				"urn:sahha:problem:file-dependency-unavailable", request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error("Unhandled File request failure requestId={} exception={}",
				RequestIdFilter.requestId(request),
				exception.getClass().getSimpleName());
		return response(HttpStatus.INTERNAL_SERVER_ERROR, problem(
				HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
				"The file request could not be completed.",
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
