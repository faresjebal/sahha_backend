package com.sahha.organisation.controller;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.ChangeStaffMembershipStatusRequest;
import com.sahha.organisation.dto.request.CreateStaffDepartmentAssignmentRequest;
import com.sahha.organisation.dto.request.EndStaffDepartmentAssignmentRequest;
import com.sahha.organisation.dto.response.DoctorProfileResponse;
import com.sahha.organisation.dto.response.StaffDepartmentAssignmentResponse;
import com.sahha.organisation.dto.response.StaffMemberPageResponse;
import com.sahha.organisation.dto.response.StaffMemberResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.security.OrganisationAccessTokenValidator;
import com.sahha.organisation.service.staffdirectoryservice.StaffDirectoryService;

@RestController
@RequestMapping("/api/v1/staff")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Organisation staff directory",
		description = "Active-organisation staff lifecycle and department placement for organisation administrators.")
public class StaffDirectoryController {

	private final StaffDirectoryService staffDirectoryService;

	public StaffDirectoryController(
			StaffDirectoryService staffDirectoryService) {
		this.staffDirectoryService = staffDirectoryService;
	}

	@GetMapping
	@Operation(
			operationId = "listOrganisationStaff",
			summary = "List doctors and receptionists in the active organisation")
	public ResponseEntity<StaffMemberPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.list(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						page,
						size));
	}

	@GetMapping("/{membershipId}")
	@Operation(
			operationId = "getOrganisationStaffMember",
			summary = "Read one staff member in the active organisation")
	public ResponseEntity<StaffMemberResponse> find(
			@PathVariable UUID membershipId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.find(
						activeOrganisationId(jwt),
						membershipId,
						actorUserId(jwt)));
	}

	@PutMapping("/{membershipId}/status")
	@Operation(
			operationId = "changeStaffMembershipStatus",
			summary = "Suspend, reactivate, or remove a staff membership")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffMemberResponse> changeStatus(
			@PathVariable UUID membershipId,
			@Valid @RequestBody ChangeStaffMembershipStatusRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.changeStatus(
						activeOrganisationId(jwt),
						membershipId,
						request,
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping("/{membershipId}/department-assignments")
	@Operation(
			operationId = "assignStaffDepartment",
			summary = "Assign an active staff member to an active department")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffDepartmentAssignmentResponse> assignDepartment(
			@PathVariable UUID membershipId,
			@Valid @RequestBody CreateStaffDepartmentAssignmentRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		StaffDepartmentAssignmentResponse created =
				staffDirectoryService.assignDepartment(
						activeOrganisationId(jwt),
						membershipId,
						request,
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(URI.create(
					"/api/v1/staff/" + membershipId
							+ "/department-assignments/" + created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@PutMapping(
			"/{membershipId}/department-assignments/{assignmentId}/end")
	@Operation(
			operationId = "endStaffDepartmentAssignment",
			summary = "End a staff member's department assignment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffDepartmentAssignmentResponse>
			endDepartmentAssignment(
					@PathVariable UUID membershipId,
					@PathVariable UUID assignmentId,
					@Valid @RequestBody
							EndStaffDepartmentAssignmentRequest request,
					@AuthenticationPrincipal Jwt jwt,
					HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.endDepartmentAssignment(
						activeOrganisationId(jwt),
						membershipId,
						assignmentId,
						request,
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest)));
	}

	@GetMapping("/{membershipId}/doctor-profile")
	@Operation(
			operationId = "getStaffDoctorProfile",
			summary = "Read a doctor's professional profile")
	public ResponseEntity<DoctorProfileResponse> doctorProfile(
			@PathVariable UUID membershipId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService
						.findDoctorProfileForAdministrator(
								activeOrganisationId(jwt),
								membershipId,
								actorUserId(jwt)));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new OrganisationAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					OrganisationAccessTokenValidator
							.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new OrganisationAccessDeniedException();
		}
	}
}
