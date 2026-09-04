package com.sahha.file.controller;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.dto.response.MedicalFileResource;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.security.FileAccessTokenValidator;
import com.sahha.file.service.medicalfiledownloadservice.AuthorizedMedicalFileDownload;
import com.sahha.file.service.medicalfiledownloadservice.MedicalFileDownloadService;

@RestController
@RequestMapping("/api/v1/files")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Medical files", description = "Private consultation attachments.")
public class MedicalFileDownloadController {

	public static final String DOWNLOAD_TOKEN_HEADER = "X-Download-Token";
	private final MedicalFileDownloadService downloadService;

	public MedicalFileDownloadController(MedicalFileDownloadService downloadService) {
		this.downloadService = downloadService;
	}

	@GetMapping
	@Operation(operationId = "listMedicalFiles",
			summary = "List safe attachment metadata for an owned consultation")
	public ResponseEntity<List<MedicalFileResource>> list(
			@RequestParam UUID consultationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(downloadService.list(
						consultationId, activeOrganisationId(jwt), actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@PostMapping("/{fileId}/download-grants")
	@Operation(operationId = "issueMedicalFileDownloadGrant",
			summary = "Issue one short-lived grant for a clean owned file")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<MedicalFileDownloadGrantResponse> issueGrant(
			@PathVariable UUID fileId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest request) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(downloadService.issueGrant(
						fileId, activeOrganisationId(jwt), actorUserId(jwt),
						jwt.getTokenValue(), RequestIdFilter.requestId(request)));
	}

	@GetMapping("/{fileId}/content")
	@Operation(operationId = "downloadMedicalFileContent",
			summary = "Consume one grant and stream clean private bytes")
	@SecurityRequirement(name = "downloadGrant")
	public ResponseEntity<InputStreamResource> download(
			@PathVariable UUID fileId,
			@RequestHeader(DOWNLOAD_TOKEN_HEADER) String downloadToken,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
			HttpServletRequest request) {
		AuthorizedMedicalFileDownload download = downloadService.download(
				fileId, activeOrganisationId(jwt), actorUserId(jwt), downloadToken,
				RequestIdFilter.requestId(request));
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment()
								.filename(download.originalFilename(), StandardCharsets.UTF_8)
								.build().toString())
				.contentType(MediaType.parseMediaType(download.contentType()))
				.contentLength(download.size())
				.body(new InputStreamResource(download.content()));
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
