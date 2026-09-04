package com.sahha.clinical.controller;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.clinical.dto.response.ClinicalAttachmentContextResponse;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.security.ClinicalAccessTokenValidator;
import com.sahha.clinical.service.consultationservice.ConsultationAttachmentAccessService;

@RestController
@RequestMapping("/api/v1/internal/clinical/consultations")
public class ClinicalAttachmentAccessController {

	private final ConsultationAttachmentAccessService accessService;

	public ClinicalAttachmentAccessController(
			ConsultationAttachmentAccessService accessService) {
		this.accessService = accessService;
	}

	@GetMapping("/{consultationId}/attachment-context")
	public ResponseEntity<ClinicalAttachmentContextResponse> resolveUploadContext(
			@PathVariable UUID consultationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(accessService.resolveUploadContext(
						activeOrganisationId(jwt), actorUserId(jwt), consultationId));
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
