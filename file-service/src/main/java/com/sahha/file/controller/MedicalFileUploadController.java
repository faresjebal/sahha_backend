package com.sahha.file.controller;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.request.NegotiateMedicalFileUploadRequest;
import com.sahha.file.dto.response.MedicalFileUploadResponse;
import com.sahha.file.dto.response.MedicalFileUploadTicketResponse;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.security.FileAccessTokenValidator;
import com.sahha.file.service.medicalfileservice.MedicalFileUploadService;

@RestController
@RequestMapping("/api/v1/files")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Medical files", description = "Private consultation attachments.")
public class MedicalFileUploadController {

	public static final String UPLOAD_TOKEN_HEADER = "X-Upload-Token";
	private final MedicalFileUploadService uploadService;

	public MedicalFileUploadController(MedicalFileUploadService uploadService) {
		this.uploadService = uploadService;
	}

	@PostMapping("/uploads")
	@Operation(operationId = "negotiateMedicalFileUpload",
			summary = "Issue one short-lived upload ticket for an owned consultation")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<MedicalFileUploadTicketResponse> negotiate(
			@Valid @RequestBody NegotiateMedicalFileUploadRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		MedicalFileUploadTicketResponse response = uploadService.negotiate(
				activeOrganisationId(jwt), actorUserId(jwt), jwt.getTokenValue(),
				request, RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(URI.create(response.uploadPath()))
				.cacheControl(CacheControl.noStore())
				.body(response);
	}

	@PutMapping(path = "/{fileId}/content")
	@Operation(operationId = "uploadMedicalFileContent",
			summary = "Consume one upload ticket and stream private bytes")
	@SecurityRequirement(name = "csrfHeader")
	@SecurityRequirement(name = "uploadTicket")
	public ResponseEntity<MedicalFileUploadResponse> upload(
			@PathVariable UUID fileId,
			@RequestHeader(UPLOAD_TOKEN_HEADER) String uploadToken,
			@RequestHeader(HttpHeaders.CONTENT_TYPE) String contentType,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) throws IOException {
		MedicalFileUploadResponse response = uploadService.upload(
				fileId, activeOrganisationId(jwt), actorUserId(jwt), uploadToken,
				contentType, httpRequest.getContentLengthLong(),
				httpRequest.getInputStream(), RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.accepted()
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
