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
import org.springframework.web.bind.annotation.RestController;
import com.sahha.clinical.config.RequestIdFilter;
import com.sahha.clinical.dto.response.SharedClinicalResourceResponse;
import com.sahha.clinical.service.consultationservice.SharedClinicalResourceService;

@RestController
@RequestMapping("/api/v1/clinical/shared")
@SecurityRequirement(name = "cookieAuth")
public class SharedClinicalResourceController {
    private final SharedClinicalResourceService service;
    public SharedClinicalResourceController(SharedClinicalResourceService service) {
        this.service = service;
    }

    @GetMapping("/{patientRegistrationId}/{resourceType}/{resourceId}")
    @Operation(operationId = "readSelectedClinicalResource",
            summary = "Read one finalized consultation or explicitly selected clinical item")
    public ResponseEntity<SharedClinicalResourceResponse> read(
            @PathVariable UUID patientRegistrationId, @PathVariable String resourceType,
            @PathVariable UUID resourceId, @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject()),
                patientRegistrationId, resourceType, resourceId, jwt.getTokenValue(),
                RequestIdFilter.requestId(request)));
    }
}
