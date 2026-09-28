package com.sahha.clinical.controller;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.clinical.config.RequestIdFilter;
import com.sahha.clinical.dto.response.SharedCareHistoryPageResponse;
import com.sahha.clinical.dto.response.SharedCareRecordResponse;
import com.sahha.clinical.dto.response.SharedCareAttachmentContextResponse;
import com.sahha.clinical.service.consultationservice.SharedCareClinicalService;

@RestController
@RequestMapping("/api/v1/clinical/shared-care/{patientRegistrationId}/consultations")
@SecurityRequirement(name = "cookieAuth")
public class SharedCareClinicalController {
    private final SharedCareClinicalService service;
    public SharedCareClinicalController(SharedCareClinicalService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "listSharedCareHistory", summary = "List finalised same-organisation encounters under active shared treatment")
    public ResponseEntity<SharedCareHistoryPageResponse> list(@PathVariable UUID patientRegistrationId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject()),
                patientRegistrationId, jwt.getTokenValue(), RequestIdFilter.requestId(request), page, size));
    }

    @GetMapping("/{consultationId}/attachment-context")
    @Operation(operationId = "readSharedCareAttachmentContext", summary = "Verify live shared care and finalised attachment ownership without clinical narratives")
    public ResponseEntity<SharedCareAttachmentContextResponse> attachmentContext(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID consultationId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.attachmentContext(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject()),
                patientRegistrationId, consultationId, jwt.getTokenValue(), RequestIdFilter.requestId(request)));
    }

    @GetMapping("/{consultationId}")
    @Operation(operationId = "readSharedCareRecord", summary = "Read one finalised record and corrections under active shared treatment")
    public ResponseEntity<SharedCareRecordResponse> read(@PathVariable UUID patientRegistrationId,
            @PathVariable UUID consultationId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject()),
                patientRegistrationId, consultationId, jwt.getTokenValue(), RequestIdFilter.requestId(request)));
    }
}
