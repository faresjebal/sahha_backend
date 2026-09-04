package com.sahha.scheduling.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.scheduling.dto.response.ClinicalAppointmentContextResponse;
import com.sahha.scheduling.dto.response.ClinicalAppointmentCompletionResponse;
import com.sahha.scheduling.dto.request.ClinicalCompletionRecoveryRequest;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.security.SchedulingAccessTokenValidator;
import com.sahha.scheduling.service.appointmentservice.ClinicalAppointmentContextService;
import com.sahha.scheduling.service.appointmentservice.ClinicalCompletionRecoveryService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/internal/clinical/appointments")
public class ClinicalAppointmentContextController {

	private final ClinicalAppointmentContextService contextService;
	private final ClinicalCompletionRecoveryService recoveryService;

	public ClinicalAppointmentContextController(
			ClinicalAppointmentContextService contextService,
			ClinicalCompletionRecoveryService recoveryService) {
		this.contextService = contextService;
		this.recoveryService = recoveryService;
	}

	@PostMapping("/{appointmentId}/completion-recovery")
	public ResponseEntity<ClinicalAppointmentCompletionResponse> recover(
			@PathVariable UUID appointmentId,
			@Valid @RequestBody ClinicalCompletionRecoveryRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(recoveryService.recover(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						appointmentId,
						request));
	}

	@GetMapping("/{appointmentId}")
	public ResponseEntity<ClinicalAppointmentContextResponse> resolve(
			@PathVariable UUID appointmentId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(contextService.resolve(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						appointmentId));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new SchedulingAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					SchedulingAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new SchedulingAccessDeniedException();
		}
	}
}
