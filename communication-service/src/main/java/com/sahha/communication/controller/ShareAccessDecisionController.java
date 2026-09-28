package com.sahha.communication.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.communication.dto.request.ShareAccessDecisionRequest;
import com.sahha.communication.dto.response.ShareAccessDecisionResponse;
import com.sahha.communication.exception.CommunicationAccessDeniedException;
import com.sahha.communication.security.CommunicationAccessTokenValidator;
import com.sahha.communication.service.referralservice.ReferralService;
import com.sahha.communication.entity.ShareResourceType;

@RestController
@RequestMapping("/api/v1/sharing/access-decisions")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Selected sharing decisions",
		description = "Exact-resource decisions for the authenticated recipient doctor.")
public class ShareAccessDecisionController {
	private final ReferralService service;

	public ShareAccessDecisionController(ReferralService service) { this.service = service; }

	@GetMapping
	@Operation(operationId = "readSharedResourceAccessDecision",
			summary = "Check one resource using ownership supplied by its owning service")
	public ResponseEntity<ShareAccessDecisionResponse> read(
			@RequestParam UUID patientRegistrationId,
			@RequestParam ShareResourceType resourceType,
			@RequestParam UUID resourceId,
			@RequestParam UUID resourceOwnerUserId,
			@RequestParam(required = false) UUID resourceOwnerMembershipId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decide(
				organisationId(jwt), userId(jwt), jwt.getTokenValue(),
				new ShareAccessDecisionRequest(patientRegistrationId, resourceType,
						resourceId, resourceOwnerUserId, resourceOwnerMembershipId)));
	}

	@PostMapping
	@Operation(operationId = "decideSharedResourceAccess",
			summary = "Decide whether one exact patient resource is actively shared")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ShareAccessDecisionResponse> decide(
			@Valid @RequestBody ShareAccessDecisionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.decide(
				organisationId(jwt), userId(jwt), jwt.getTokenValue(), request));
	}

	private static UUID userId(Jwt jwt) {
		try { return UUID.fromString(jwt.getSubject()); }
		catch (RuntimeException invalid) { throw new CommunicationAccessDeniedException(); }
	}
	private static UUID organisationId(Jwt jwt) {
		try { return UUID.fromString(jwt.getClaimAsString(
				CommunicationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM)); }
		catch (RuntimeException invalid) { throw new CommunicationAccessDeniedException(); }
	}
}
