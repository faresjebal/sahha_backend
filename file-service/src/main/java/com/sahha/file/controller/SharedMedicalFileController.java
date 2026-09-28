package com.sahha.file.controller;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.sahha.file.config.RequestIdFilter;
import com.sahha.file.dto.response.MedicalFileResource;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.service.medicalfiledownloadservice.SharedMedicalFileService;

@RestController
@RequestMapping("/api/v1/files/shared/{patientRegistrationId}/{fileId}")
@SecurityRequirement(name = "cookieAuth")
public class SharedMedicalFileController {
    private final SharedMedicalFileService service;
    public SharedMedicalFileController(SharedMedicalFileService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "readSelectedMedicalFileMetadata", summary = "Read metadata for one shared file")
    public ResponseEntity<MedicalFileResource> metadata(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.metadata(
                organisation(jwt), actor(jwt), patientRegistrationId, fileId, jwt.getTokenValue(),
                RequestIdFilter.requestId(request)));
    }

    @PostMapping("/download-grants")
    @Operation(operationId = "issueSelectedMedicalFileDownload", summary = "Issue a download token bounded by the share expiry")
    @SecurityRequirement(name = "csrfHeader")
    public ResponseEntity<MedicalFileDownloadGrantResponse> issue(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(
                organisation(jwt), actor(jwt), patientRegistrationId, fileId, jwt.getTokenValue(),
                RequestIdFilter.requestId(request)));
    }

    @GetMapping("/content")
    @Operation(operationId = "downloadSelectedMedicalFile", summary = "Revalidate the share and consume a one-time token")
    @SecurityRequirement(name = "downloadGrant")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @RequestHeader("X-Download-Token") String downloadToken,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        var download = service.download(organisation(jwt), actor(jwt), patientRegistrationId, fileId,
                jwt.getTokenValue(), downloadToken, RequestIdFilter.requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size()).body(new InputStreamResource(download.content()));
    }

    private static UUID organisation(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("org_id")); }
    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
