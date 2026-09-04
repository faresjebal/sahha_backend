package com.sahha.file.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.request.SyntheticFileScanDecisionRequest;
import com.sahha.file.dto.response.MedicalFileScanResponse;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.security.FileAccessTokenValidator;
import com.sahha.file.service.medicalfilescanservice.SyntheticMedicalFileScanService;

@RestController
@RequestMapping("/api/v1/files")
@ConditionalOnProperty(
		name = "sahha.file.storage.synthetic-clean-enabled",
		havingValue = "true")
@SecurityRequirement(name = "cookieAuth")
public class SyntheticFileScanController {

	private final SyntheticMedicalFileScanService scanService;

	public SyntheticFileScanController(
			SyntheticMedicalFileScanService scanService) {
		this.scanService = scanService;
	}

	@PostMapping("/{fileId}/synthetic-scan")
	@Operation(
			operationId = "applySyntheticLocalFileScanDecision",
			summary = "Apply an explicit local-only synthetic scan decision",
			description = "Development aid only; this is not a malware scanner.")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<MedicalFileScanResponse> decide(
			@PathVariable UUID fileId,
			@Valid @RequestBody SyntheticFileScanDecisionRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		MedicalFileScanResponse response = scanService.apply(
				fileId, activeOrganisationId(jwt), actorUserId(jwt),
				request.decision(), RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(response);
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new FileAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					FileAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new FileAccessDeniedException();
		}
	}
}
