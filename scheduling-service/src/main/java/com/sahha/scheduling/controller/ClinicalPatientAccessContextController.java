package com.sahha.scheduling.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.scheduling.dto.response.ClinicalPatientAccessContextResponse;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;
import com.sahha.scheduling.security.SchedulingAccessTokenValidator;
import com.sahha.scheduling.service.appointmentservice.ClinicalPatientAccessContextService;

@RestController
@RequestMapping("/api/v1/internal/clinical/patients")
public class ClinicalPatientAccessContextController {

	private final ClinicalPatientAccessContextService contextService;

	public ClinicalPatientAccessContextController(
			ClinicalPatientAccessContextService contextService) {
		this.contextService = contextService;
	}

	@GetMapping("/{patientRegistrationId}/access-context")
	public ResponseEntity<ClinicalPatientAccessContextResponse> resolve(
			@PathVariable UUID patientRegistrationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(contextService.resolve(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						patientRegistrationId));
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
