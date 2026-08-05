package com.sahha.organisation.controller;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.UpsertDoctorProfileRequest;
import com.sahha.organisation.dto.response.DoctorProfileResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.security.OrganisationAccessTokenValidator;
import com.sahha.organisation.service.staffdirectoryservice.StaffDirectoryService;

@RestController
@RequestMapping("/api/v1/my/doctor-profile")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "My doctor profile",
		description = "The active organisation-specific professional profile owned by the authenticated doctor.")
public class MyDoctorProfileController {

	private final StaffDirectoryService staffDirectoryService;

	public MyDoctorProfileController(
			StaffDirectoryService staffDirectoryService) {
		this.staffDirectoryService = staffDirectoryService;
	}

	@GetMapping
	@Operation(
			operationId = "getMyDoctorProfile",
			summary = "Read the caller's professional profile")
	public ResponseEntity<DoctorProfileResponse> find(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.findMyDoctorProfile(
						activeOrganisationId(jwt),
						actorUserId(jwt)));
	}

	@PutMapping
	@Operation(
			operationId = "upsertMyDoctorProfile",
			summary = "Create or update the caller's professional profile")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<DoctorProfileResponse> upsert(
			@Valid @RequestBody UpsertDoctorProfileRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(staffDirectoryService.upsertMyDoctorProfile(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						request,
						RequestIdFilter.requestId(httpRequest)));
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
