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

	@ExceptionHandler(DepartmentNotFoundException.class)
	ResponseEntity<ProblemDetail> departmentNotFound(
			DepartmentNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Department not found",
						"The requested department was not found in the active organisation.",
						"urn:sahha:problem:department-not-found",
						request));
	}

	@ExceptionHandler(StaffInvitationNotFoundException.class)
	ResponseEntity<ProblemDetail> staffInvitationNotFound(
			StaffInvitationNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Staff invitation not found",
						"The requested staff invitation was not found or is not available to this account.",
						"urn:sahha:problem:staff-invitation-not-found",
						request));
	}

	@ExceptionHandler(StaffMemberNotFoundException.class)
	ResponseEntity<ProblemDetail> staffMemberNotFound(
			StaffMemberNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Staff member not found",
						"The requested staff member was not found in the active organisation.",
						"urn:sahha:problem:staff-member-not-found",
						request));
	}

	@ExceptionHandler(StaffDepartmentAssignmentNotFoundException.class)
	ResponseEntity<ProblemDetail> staffAssignmentNotFound(
			StaffDepartmentAssignmentNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Department assignment not found",
						"The requested department assignment was not found in the active organisation.",
						"urn:sahha:problem:staff-department-assignment-not-found",
						request));
	}

	@ExceptionHandler(DoctorProfileNotFoundException.class)
	ResponseEntity<ProblemDetail> doctorProfileNotFound(
			DoctorProfileNotFoundException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Doctor profile not found",
						"The professional profile has not been created in the active organisation.",
						"urn:sahha:problem:doctor-profile-not-found",
						request));
	}

	@ExceptionHandler(OrganisationAccessDeniedException.class)
	ResponseEntity<ProblemDetail> organisationAccessDenied(
			OrganisationAccessDeniedException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.FORBIDDEN,
				problem(
						HttpStatus.FORBIDDEN,
						"Organisation access denied",
						"The active organisation context does not permit this operation.",
						"urn:sahha:problem:organisation-access-denied",
						request));
	}

	@ExceptionHandler(DepartmentConflictException.class)
	ResponseEntity<ProblemDetail> departmentConflict(
			DepartmentConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Department conflict",
						"A department with the same name or code already exists in this organisation.",
						"urn:sahha:problem:department-conflict",
						request));
	}

	@ExceptionHandler(ConcurrentDepartmentModificationException.class)
	ResponseEntity<ProblemDetail> concurrentDepartmentModification(
			ConcurrentDepartmentModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Department changed",
						"The department changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-department-modification",
						request));
	}

	@ExceptionHandler(ConcurrentStaffInvitationModificationException.class)
	ResponseEntity<ProblemDetail> concurrentStaffInvitationModification(
			ConcurrentStaffInvitationModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Staff invitation changed",
						"The staff invitation changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-staff-invitation-modification",
						request));
	}

	@ExceptionHandler(StaffInvitationConflictException.class)
	ResponseEntity<ProblemDetail> staffInvitationConflict(
			StaffInvitationConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Staff invitation conflict",
						"The invitation is expired, already resolved, duplicated, or conflicts with an existing membership.",
						"urn:sahha:problem:staff-invitation-conflict",
						request));
	}

	@ExceptionHandler(ConcurrentStaffResourceModificationException.class)
	ResponseEntity<ProblemDetail> concurrentStaffResourceModification(
			ConcurrentStaffResourceModificationException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Staff resource changed",
						"The staff resource changed since it was loaded. Refresh and try again.",
						"urn:sahha:problem:concurrent-staff-resource-modification",
						request));
	}

	@ExceptionHandler(StaffManagementConflictException.class)
	ResponseEntity<ProblemDetail> staffManagementConflict(
			StaffManagementConflictException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Staff management conflict",
						"The requested lifecycle, department, role, or professional-profile change conflicts with current organisation state.",
						"urn:sahha:problem:staff-management-conflict",
						request));
	}

	@ExceptionHandler({
		OrganisationContextNotFoundException.class,
		OrganisationMembershipNotFoundException.class,
		EligibleAccountNotFoundException.class
	})
	ResponseEntity<ProblemDetail> membershipTargetNotFound(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.NOT_FOUND,
				problem(
						HttpStatus.NOT_FOUND,
						"Membership resource not found",
						"The requested membership resource or eligible account was not found.",
						"urn:sahha:problem:membership-resource-not-found",
						request));
	}

	@ExceptionHandler({
		OrganisationMembershipConflictException.class,
		AccountNotEligibleException.class
	})
	ResponseEntity<ProblemDetail> membershipConflict(
			RuntimeException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.CONFLICT,
				problem(
						HttpStatus.CONFLICT,
						"Membership conflict",
						"The account is not eligible or already has a membership in this organisation.",
						"urn:sahha:problem:membership-conflict",
						request));
	}

	@ExceptionHandler(AuthAccountDirectoryUnavailableException.class)
	ResponseEntity<ProblemDetail> authDirectoryUnavailable(
			AuthAccountDirectoryUnavailableException exception,
			HttpServletRequest request) {
		return response(
				HttpStatus.SERVICE_UNAVAILABLE,
				problem(
						HttpStatus.SERVICE_UNAVAILABLE,
						"Identity directory unavailable",
						"The identity could not be verified at this time. Try again later.",
						"urn:sahha:problem:identity-directory-unavailable",
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
