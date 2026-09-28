package com.sahha.communication.controller;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import com.sahha.communication.dto.request.CreateReferralRequest;
import com.sahha.communication.dto.request.ReferralReasonedCommandRequest;
import com.sahha.communication.dto.request.ReferralVersionRequest;
import com.sahha.communication.dto.response.ReferralPageResponse;
import com.sahha.communication.dto.response.ReferralResponse;
import com.sahha.communication.exception.CommunicationAccessDeniedException;
import com.sahha.communication.security.CommunicationAccessTokenValidator;
import com.sahha.communication.service.referralservice.ReferralDirection;
import com.sahha.communication.service.referralservice.ReferralService;

@RestController
@RequestMapping("/api/v1/referrals")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Doctor referrals",
		description = "Participant-scoped referrals with explicit consent and selected-resource sharing.")
public class ReferralController {
	private final ReferralService service;

	public ReferralController(ReferralService service) { this.service = service; }

	@PostMapping
	@Operation(operationId = "createReferral", summary = "Create a referral draft or send it immediately")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> create(
			@Valid @RequestBody CreateReferralRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		ReferralResponse created = service.create(organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request);
		return ResponseEntity.created(URI.create("/api/v1/referrals/" + created.id()))
				.cacheControl(CacheControl.noStore()).body(created);
	}

	@GetMapping
	@Operation(operationId = "listReferrals", summary = "List participant-scoped referrals")
	public ResponseEntity<ReferralPageResponse> list(
			@RequestParam(defaultValue = "ALL") ReferralDirection direction,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "50") int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(
				organisationId(jwt), userId(jwt), jwt.getTokenValue(), direction, page, size));
	}

	@GetMapping("/{referralId}")
	@Operation(operationId = "getReferral", summary = "Read one participant-scoped referral")
	public ResponseEntity<ReferralResponse> find(@PathVariable UUID referralId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find(
				referralId, organisationId(jwt), userId(jwt), jwt.getTokenValue()));
	}

	@PostMapping("/{referralId}/send")
	@Operation(operationId = "sendReferral", summary = "Send a referral draft")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> send(@PathVariable UUID referralId,
			@Valid @RequestBody ReferralVersionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return command(service.send(referralId, organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request));
	}

	@PostMapping("/{referralId}/accept")
	@Operation(operationId = "acceptReferral", summary = "Accept a received referral and activate its grant")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> accept(@PathVariable UUID referralId,
			@Valid @RequestBody ReferralVersionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return command(service.accept(referralId, organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request));
	}

	@PostMapping("/{referralId}/reject")
	@Operation(operationId = "rejectReferral", summary = "Reject a received referral")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> reject(@PathVariable UUID referralId,
			@Valid @RequestBody ReferralReasonedCommandRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return command(service.reject(referralId, organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request));
	}

	@PostMapping("/{referralId}/revoke")
	@Operation(operationId = "revokeReferral", summary = "Immediately revoke a sent or active referral")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> revoke(@PathVariable UUID referralId,
			@Valid @RequestBody ReferralReasonedCommandRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return command(service.revoke(referralId, organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request));
	}

	@PostMapping("/{referralId}/complete")
	@Operation(operationId = "completeReferral", summary = "Complete an active referral")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ReferralResponse> complete(@PathVariable UUID referralId,
			@Valid @RequestBody ReferralVersionRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return command(service.complete(referralId, organisationId(jwt), userId(jwt),
				jwt.getTokenValue(), request));
	}

	private static ResponseEntity<ReferralResponse> command(ReferralResponse response) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
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
