package com.sahha.clinical.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.dto.request.RecoverAppointmentCompletionRequest;
import com.sahha.clinical.dto.response.AppointmentCompletionRecoveryResponse;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.security.ClinicalAccessTokenValidator;
import com.sahha.clinical.service.consultationservice.AppointmentCompletionRecoveryService;

@RestController
@RequestMapping("/api/v1/consultations")
@SecurityRequirement(name = "cookieAuth")
public class AppointmentCompletionRecoveryController {

	private final AppointmentCompletionRecoveryService recoveryService;
	private final String csrfHeaderName;

	public AppointmentCompletionRecoveryController(
			AppointmentCompletionRecoveryService recoveryService,
			ClinicalSecurityProperties securityProperties) {
		this.recoveryService = recoveryService;
		this.csrfHeaderName = securityProperties.csrfHeaderName();
	}

	@PostMapping("/{consultationId}/appointment-completion-recovery")
	@Operation(operationId = "recoverAppointmentCompletion",
			summary = "Retry appointment completion from the persisted finalisation event")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AppointmentCompletionRecoveryResponse> recover(
			@PathVariable UUID consultationId,
			@Valid @RequestBody RecoverAppointmentCompletionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(recoveryService.recover(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						consultationId,
						request,
						jwt.getTokenValue(),
						httpRequest.getHeader(csrfHeaderName)));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new ClinicalAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					ClinicalAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new ClinicalAccessDeniedException();
		}
	}
}
