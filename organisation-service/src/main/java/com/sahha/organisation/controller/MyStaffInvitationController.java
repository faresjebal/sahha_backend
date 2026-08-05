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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.StaffInvitationVersionRequest;
import com.sahha.organisation.dto.response.StaffInvitationPageResponse;
import com.sahha.organisation.dto.response.StaffInvitationResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.service.staffinvitationservice.StaffInvitationIdentityService;

@RestController
@RequestMapping("/api/v1/my/staff-invitations")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "My staff invitations",
		description = "Caller-owned invitation decisions verified against the Auth account email.")
public class MyStaffInvitationController {

	private final StaffInvitationIdentityService invitationService;

	public MyStaffInvitationController(
			StaffInvitationIdentityService invitationService) {
		this.invitationService = invitationService;
	}

	@GetMapping
	@Operation(
			operationId = "listMyStaffInvitations",
			summary = "List staff invitations addressed to the current account")
	public ResponseEntity<StaffInvitationPageResponse> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.listMine(
						actorUserId(jwt),
						jwt.getTokenValue(),
						page,
						size));
	}

	@PostMapping("/{invitationId}/accept")
	@Operation(
			operationId = "acceptMyStaffInvitation",
			summary = "Accept an invitation addressed to the current account")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffInvitationResponse> accept(
			@PathVariable UUID invitationId,
			@Valid @RequestBody StaffInvitationVersionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.accept(
						invitationId,
						request.version(),
						actorUserId(jwt),
						jwt.getTokenValue(),
						RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping("/{invitationId}/reject")
	@Operation(
			operationId = "rejectMyStaffInvitation",
			summary = "Reject an invitation addressed to the current account")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<StaffInvitationResponse> reject(
			@PathVariable UUID invitationId,
			@Valid @RequestBody StaffInvitationVersionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(invitationService.reject(
						invitationId,
						request.version(),
						actorUserId(jwt),
						jwt.getTokenValue(),
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
}
