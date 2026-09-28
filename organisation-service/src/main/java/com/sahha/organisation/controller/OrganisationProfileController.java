package com.sahha.organisation.controller;

import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.UpdateOrganisationProfileRequest;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.service.organisationservice.OrganisationProfileService;

@RestController
@RequestMapping("/api/v1/organisations/current/profile")
@SecurityRequirement(name = "cookieAuth")
public class OrganisationProfileController {
    private final OrganisationProfileService service;
    public OrganisationProfileController(OrganisationProfileService service) { this.service = service; }
    @GetMapping
    @Operation(operationId = "getCurrentOrganisationProfile", summary = "Read the active administrator's organisation profile")
    public ResponseEntity<OrganisationResponse> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.get(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject())));
    }
    @PutMapping
    @SecurityRequirement(name = "csrfHeader")
    @Operation(operationId = "updateCurrentOrganisationProfile", summary = "Update administrative profile fields with an explicit version")
    public ResponseEntity<OrganisationResponse> update(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateOrganisationProfileRequest command, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(
                UUID.fromString(jwt.getClaimAsString("org_id")), UUID.fromString(jwt.getSubject()),
                command, RequestIdFilter.requestId(request)));
    }
}
