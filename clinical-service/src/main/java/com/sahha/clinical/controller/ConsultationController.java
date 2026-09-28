package com.sahha.clinical.controller;

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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.clinical.config.RequestIdFilter;
import com.sahha.clinical.dto.request.CreateConsultationRequest;
import com.sahha.clinical.dto.request.CreateClinicalCorrectionRequest;
import com.sahha.clinical.dto.request.FinalizeConsultationRequest;
import com.sahha.clinical.dto.request.ReplaceConsultationDraftRequest;
import com.sahha.clinical.dto.request.UpdateConsultationDraftRequest;
import com.sahha.clinical.dto.response.ClinicalRecordResponse;
import com.sahha.clinical.dto.response.ConsultationResponse;
import com.sahha.clinical.dto.response.ReferralSourcePageResponse;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.security.ClinicalAccessTokenValidator;
import com.sahha.clinical.service.consultationservice.ConsultationCreationResult;
import com.sahha.clinical.service.consultationservice.ConsultationService;
import com.sahha.clinical.service.consultationservice.ClinicalRecordService;
import com.sahha.clinical.service.consultationservice.ReferralSourceService;
import com.sahha.clinical.service.consultationservice.ConsultationAuthorAccessService;

@RestController
@RequestMapping("/api/v1/consultations")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Consultations", description = "Authorised doctor consultation drafts.")
public class ConsultationController {

	private final ConsultationService consultationService;
	private final ClinicalRecordService clinicalRecordService;
	private final ReferralSourceService referralSources;
	private final ConsultationAuthorAccessService authorAccess;

	public ConsultationController(
			ConsultationService consultationService,
			ClinicalRecordService clinicalRecordService,
			ReferralSourceService referralSources,
			ConsultationAuthorAccessService authorAccess) {
		this.consultationService = consultationService;
		this.clinicalRecordService = clinicalRecordService;
		this.referralSources = referralSources;
		this.authorAccess = authorAccess;
	}

	@GetMapping("/referral-sources")
	@Operation(operationId = "listOwnedReferralSources",
			summary = "List finalised sources authored under the caller's live doctor membership")
	public ResponseEntity<ReferralSourcePageResponse> referralSources(
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(referralSources.list(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				RequestIdFilter.requestId(request), page, size));
	}

	@GetMapping("/{consultationId}/record")
	@Operation(operationId = "getClinicalRecord",
			summary = "Read the effective structured record and correction history")
	public ResponseEntity<ClinicalRecordResponse> findRecord(
			@PathVariable UUID consultationId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(clinicalRecordService.find(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId,
						RequestIdFilter.requestId(httpRequest)));
	}

	@PutMapping("/{consultationId}/draft-content")
	@Operation(operationId = "replaceConsultationDraftContent",
			summary = "Replace all structured draft content under one version")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ClinicalRecordResponse> replaceDraftContent(
			@PathVariable UUID consultationId,
			@Valid @RequestBody ReplaceConsultationDraftRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(clinicalRecordService.replaceDraft(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId,
						request, RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping("/{consultationId}/finalize")
	@Operation(operationId = "finalizeConsultation",
			summary = "Sign a complete draft and make its source immutable")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ClinicalRecordResponse> finalizeRecord(
			@PathVariable UUID consultationId,
			@Valid @RequestBody FinalizeConsultationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(clinicalRecordService.finalizeRecord(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId,
						request, RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping("/{consultationId}/corrections")
	@Operation(operationId = "correctFinalizedConsultation",
			summary = "Append an attributable field correction to a signed record")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ClinicalRecordResponse> correct(
			@PathVariable UUID consultationId,
			@Valid @RequestBody CreateClinicalCorrectionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(clinicalRecordService.correct(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId,
						request, RequestIdFilter.requestId(httpRequest)));
	}

	@PostMapping
	@Operation(operationId = "createConsultationDraft",
			summary = "Create or recover the draft for an in-progress owned appointment")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ConsultationResponse> create(
			@Valid @RequestBody CreateConsultationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		ConsultationCreationResult result = consultationService.create(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				request.appointmentId(), RequestIdFilter.requestId(httpRequest));
		if (!result.created()) {
			return ResponseEntity.ok()
					.cacheControl(CacheControl.noStore())
					.body(result.consultation());
		}
		return ResponseEntity.created(URI.create(
				"/api/v1/consultations/" + result.consultation().id()))
				.cacheControl(CacheControl.noStore())
				.body(result.consultation());
	}

	@GetMapping("/{consultationId}")
	@Operation(operationId = "getConsultationDraft",
			summary = "Read one consultation owned by the authenticated doctor")
	public ResponseEntity<ConsultationResponse> find(
			@PathVariable UUID consultationId,
			@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(consultationService.find(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId));
	}

	@PatchMapping("/{consultationId}/draft")
	@Operation(operationId = "updateConsultationDraft",
			summary = "Update the current draft with optimistic version protection")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<ConsultationResponse> updateDraft(
			@PathVariable UUID consultationId,
			@Valid @RequestBody UpdateConsultationDraftRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		requireCurrentAuthor(consultationId, jwt, httpRequest);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(consultationService.updateDraft(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId,
						request, RequestIdFilter.requestId(httpRequest)));
	}

	private void requireCurrentAuthor(UUID consultationId, Jwt jwt, HttpServletRequest request) {
		authorAccess.require(activeOrganisationId(jwt), actorUserId(jwt), consultationId,
				jwt.getTokenValue(), RequestIdFilter.requestId(request));
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
