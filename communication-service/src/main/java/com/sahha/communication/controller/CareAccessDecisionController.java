package com.sahha.communication.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.communication.dto.response.CareAccessDecisionResponse;
import com.sahha.communication.exception.CommunicationAccessDeniedException;
import com.sahha.communication.security.CommunicationAccessTokenValidator;
import com.sahha.communication.service.referralservice.ReferralCareAccessService;

@RestController
@RequestMapping("/api/v1/sharing/care-access-decisions")
@SecurityRequirement(name = "cookieAuth")
public class CareAccessDecisionController {
    private final ReferralCareAccessService service;
    public CareAccessDecisionController(ReferralCareAccessService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "readSharedTreatmentAccessDecision",
            summary = "Check the current doctor's accepted same-organisation care participation")
    public ResponseEntity<CareAccessDecisionResponse> read(@RequestParam UUID patientRegistrationId,
            @AuthenticationPrincipal Jwt jwt) {
        UUID organisationId, userId;
        try {
            organisationId = UUID.fromString(jwt.getClaimAsString(
                    CommunicationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
            userId = UUID.fromString(jwt.getSubject());
        }
        catch (RuntimeException invalid) { throw new CommunicationAccessDeniedException(); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.decide(organisationId, patientRegistrationId, userId, jwt.getTokenValue()));
    }
}
