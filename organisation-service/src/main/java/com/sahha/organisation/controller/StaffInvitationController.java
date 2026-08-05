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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.CreateStaffInvitationRequest;
import com.sahha.organisation.dto.request.StaffInvitationVersionRequest;
import com.sahha.organisation.dto.response.StaffInvitationPageResponse;
import com.sahha.organisation.dto.response.StaffInvitationResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.security.OrganisationAccessTokenValidator;
import com.sahha.organisation.service.staffinvitationservice.StaffInvitationService;

@RestController
@RequestMapping("/api/v1/staff-invitations")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Organisation staff invitations",
		description = "Active-organisation onboarding controls for organisation administrators.")
public class StaffInvitationController {

	private final StaffInvitationService invitationService;

	public StaffInvitationController(
			StaffInvitationService invitationService) {
		this.invitationService = invitationService;
	}

	@PostMapping
	@Operation(
			operationId = "createStaffInvitation",
			summary = "Invite a doctor or receptionist to the active organisation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffInvitationResponse> create(
			@Valid @RequestBody CreateStaffInvitationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		StaffInvitationResponse created = invitationService.create(
				activeOrganisationId(jwt),
				request,
				actorUserId(jwt),
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(URI.create(
					"/api/v1/staff-invitations/" + created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@GetMapping
	@Operation(
			operationId = "listStaffInvitations",
			summary = "List invitations in the active organisation")
	public ResponseEntity<StaffInvitationPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.listForAdministrator(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						page,
						size));
	}

	@GetMapping("/{invitationId}")
	@Operation(
			operationId = "getStaffInvitation",
			summary = "Read an invitation in the active organisation")
	public ResponseEntity<StaffInvitationResponse> find(
			@PathVariable UUID invitationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.findForAdministrator(
						activeOrganisationId(jwt),
						invitationId,
						actorUserId(jwt)));
	}

	@PostMapping("/{invitationId}/renew")
	@Operation(
			operationId = "renewStaffInvitation",
			summary = "Renew a pending or expired invitation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffInvitationResponse> renew(
			@PathVariable UUID invitationId,
			@Valid @RequestBody StaffInvitationVersionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.renew(
						activeOrganisationId(jwt),
						invitationId,
						request.version(),
						actorUserId(jwt),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping("/{invitationId}/revoke")
	@Operation(
			operationId = "revokeStaffInvitation",
			summary = "Revoke a pending invitation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffInvitationResponse> revoke(
			@PathVariable UUID invitationId,
			@Valid @RequestBody StaffInvitationVersionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.revoke(
						activeOrganisationId(jwt),
						invitationId,
						request.version(),
						actorUserId(jwt),
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
