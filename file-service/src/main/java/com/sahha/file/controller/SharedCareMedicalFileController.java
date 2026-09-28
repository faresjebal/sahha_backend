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
import com.sahha.file.dto.response.*;
import com.sahha.file.service.medicalfiledownloadservice.SharedCareMedicalFileService;

@RestController
@RequestMapping("/api/v1/files/shared-care/{patientRegistrationId}")
@SecurityRequirement(name = "cookieAuth")
public class SharedCareMedicalFileController {
    private final SharedCareMedicalFileService service;
    public SharedCareMedicalFileController(SharedCareMedicalFileService service) { this.service = service; }

    @GetMapping("/consultations/{consultationId}")
    @Operation(operationId = "listSharedCareFiles", summary = "List clean documents for a finalised shared-care encounter")
    public ResponseEntity<SharedCareFilePageResponse> list(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID consultationId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(organisation(jwt), actor(jwt),
                patientRegistrationId, consultationId, jwt.getTokenValue(), RequestIdFilter.requestId(request), page, size));
    }

    @GetMapping("/{fileId}")
    @Operation(operationId = "readSharedCareFileMetadata", summary = "Read one clean shared-care file's metadata")
    public ResponseEntity<SharedCareFileResponse> metadata(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.metadata(organisation(jwt), actor(jwt),
                patientRegistrationId, fileId, jwt.getTokenValue(), RequestIdFilter.requestId(request)));
    }

    @PostMapping("/{fileId}/download-grants")
    @Operation(operationId = "issueSharedCareFileDownload", summary = "Revalidate shared care and issue a scoped one-time token")
    @SecurityRequirement(name = "csrfHeader")
    public ResponseEntity<MedicalFileDownloadGrantResponse> issue(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.issue(organisation(jwt), actor(jwt),
                patientRegistrationId, fileId, jwt.getTokenValue(), RequestIdFilter.requestId(request)));
    }

    @GetMapping("/{fileId}/content")
    @Operation(operationId = "downloadSharedCareFile", summary = "Revalidate live care and Clinical context before releasing bytes")
    @SecurityRequirement(name = "downloadGrant")
    public ResponseEntity<InputStreamResource> download(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID fileId, @RequestHeader("X-Download-Token") String downloadToken,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        var download = service.download(organisation(jwt), actor(jwt), patientRegistrationId, fileId,
                jwt.getTokenValue(), downloadToken, RequestIdFilter.requestId(request));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.originalFilename(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(download.contentType())).contentLength(download.size())
                .body(new InputStreamResource(download.content()));
    }
    private static UUID organisation(Jwt jwt) { return UUID.fromString(jwt.getClaimAsString("org_id")); }
    private static UUID actor(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
}
