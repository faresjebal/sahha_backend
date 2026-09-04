package com.sahha.clinical.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import com.sahha.clinical.dto.response.PatientClinicalSummaryResponse;
import com.sahha.clinical.config.RequestIdFilter;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.security.ClinicalAccessTokenValidator;
import com.sahha.clinical.service.consultationservice.PatientClinicalSummaryService;

@RestController
@RequestMapping("/api/v1/clinical/patients")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Clinical summary",
		description = "Minimum-necessary summaries under a live care relationship.")
public class PatientClinicalSummaryController {

	private final PatientClinicalSummaryService summaryService;

	public PatientClinicalSummaryController(
			PatientClinicalSummaryService summaryService) {
		this.summaryService = summaryService;
	}

	@GetMapping("/{patientRegistrationId}/summary")
	@Operation(operationId = "getPatientClinicalSummary",
			summary = "Read a bounded summary after a live Scheduling decision")
	public ResponseEntity<PatientClinicalSummaryResponse> find(
			@PathVariable UUID patientRegistrationId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(summaryService.find(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						patientRegistrationId,
						RequestIdFilter.requestId(httpRequest)));
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
