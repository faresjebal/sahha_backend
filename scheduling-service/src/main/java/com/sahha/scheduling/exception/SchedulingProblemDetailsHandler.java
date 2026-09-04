package com.sahha.scheduling.exception;

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
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.sahha.scheduling.config.RequestIdFilter;

@RestControllerAdvice
public class SchedulingProblemDetailsHandler {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(SchedulingProblemDetailsHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ProblemDetail> validation(
			MethodArgumentNotValidException exception,
			HttpServletRequest request) {
		ProblemDetail problem = problem(
				HttpStatus.BAD_REQUEST,
				"Invalid scheduling request",
				"One or more scheduling fields are invalid.",
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

	@ExceptionHandler({
		HttpMessageNotReadableException.class,
		MissingServletRequestParameterException.class,
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
						"Invalid scheduling request",
						"The availability rules, requested slot range, or appointment command is invalid.",
						"urn:sahha:problem:invalid-scheduling-request",
						request));
	}

	@ExceptionHandler(AppointmentPatientNotFoundException.class)
	ResponseEntity<ProblemDetail> patientNotFound(
			AppointmentPatientNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Patient registration not found",
						"The patient is not actively registered in the current organisation.",
						"urn:sahha:problem:appointment-patient-not-found",
						request));
	}

	@ExceptionHandler(AppointmentNotFoundException.class)
	ResponseEntity<ProblemDetail> appointmentNotFound(
			AppointmentNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Appointment not found",
						"The requested appointment was not found in the active organisation.",
						"urn:sahha:problem:appointment-not-found",
						request));
	}

	@ExceptionHandler(AppointmentDoctorNotFoundException.class)
	ResponseEntity<ProblemDetail> doctorNotFound(
			AppointmentDoctorNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Scheduling doctor not found",
						"The doctor is not active and schedulable in the current organisation.",
						"urn:sahha:problem:appointment-doctor-not-found",
						request));
	}

	@ExceptionHandler(AppointmentSlotUnavailableException.class)
	ResponseEntity<ProblemDetail> slotUnavailable(
			AppointmentSlotUnavailableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Appointment slot unavailable",
						"That slot is no longer available. Refresh the schedule and choose another time.",
						"urn:sahha:problem:appointment-slot-unavailable",
						request));
	}

	@ExceptionHandler(BookingRequestConflictException.class)
	ResponseEntity<ProblemDetail> bookingRequestConflict(
			BookingRequestConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Booking request conflict",
						"That booking request identifier was already used for different appointment details.",
						"urn:sahha:problem:booking-request-conflict",
						request));
	}

	@ExceptionHandler(AppointmentCommandConflictException.class)
	ResponseEntity<ProblemDetail> appointmentCommandConflict(
			AppointmentCommandConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Appointment command conflict",
						"That command identifier was already used for different appointment details.",
						"urn:sahha:problem:appointment-command-conflict",
						request));
	}

	@ExceptionHandler(InvalidAppointmentTransitionException.class)
	ResponseEntity<ProblemDetail> invalidAppointmentTransition(
			InvalidAppointmentTransitionException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Appointment transition not allowed",
						"The appointment cannot move from its current status to the requested status.",
						"urn:sahha:problem:invalid-appointment-transition",
						request));
	}

	@ExceptionHandler(AppointmentNotReadyForNoShowException.class)
	ResponseEntity<ProblemDetail> appointmentNotReadyForNoShow(
			AppointmentNotReadyForNoShowException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Appointment has not started",
						"An appointment cannot be marked as no-show before its scheduled start time.",
						"urn:sahha:problem:appointment-not-ready-for-no-show",
						request));
	}

	@ExceptionHandler(ConcurrentAppointmentModificationException.class)
	ResponseEntity<ProblemDetail> concurrentAppointmentModification(
			ConcurrentAppointmentModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Appointment changed",
						"The appointment changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-appointment-modification",
						request));
	}

	@ExceptionHandler(SchedulingAccessDeniedException.class)
	ResponseEntity<ProblemDetail> denied(
			SchedulingAccessDeniedException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				problem(
						HttpStatus.FORBIDDEN,
						"Scheduling access denied",
						"The active organisation context does not permit this scheduling operation.",
						"urn:sahha:problem:scheduling-access-denied",
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

	@ExceptionHandler(PatientRegistryUnavailableException.class)
	ResponseEntity<ProblemDetail> patientRegistryUnavailable(
			PatientRegistryUnavailableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				problem(
						HttpStatus.SERVICE_UNAVAILABLE,
						"Patient registry unavailable",
						"The patient registration could not be verified. Try again later.",
						"urn:sahha:problem:patient-registry-unavailable",
						request));
	}

	@ExceptionHandler(AvailabilityNotFoundException.class)
	ResponseEntity<ProblemDetail> notFound(
			AvailabilityNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Doctor availability not found",
						"No availability exists for that doctor in the active organisation.",
						"urn:sahha:problem:availability-not-found",
						request));
	}

	@ExceptionHandler(ConcurrentAvailabilityModificationException.class)
	ResponseEntity<ProblemDetail> conflict(
			ConcurrentAvailabilityModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Availability changed",
						"The availability changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-availability-modification",
						request));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> unexpected(
			Exception exception,
			HttpServletRequest request) {
		LOGGER.error(
				"Unhandled Scheduling request failure requestId={} exception={}",
				RequestIdFilter.requestId(request),
				exception.getClass().getSimpleName());
		return response(
				HttpStatus.INTERNAL_SERVER_ERROR,
				problem(
						HttpStatus.INTERNAL_SERVER_ERROR,
						"Internal server error",
						"The scheduling request could not be completed.",
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
