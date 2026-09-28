package com.sahha.clinical.client.communication;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.SharingContextUnavailableException;

/** No local positive cache, appointment fallback or caller-supplied doctor identity. */
@Component
public class CommunicationCareAccessClient {
    private final RestClient client;
    private final String cookieName;
    private final Clock clock;

    public CommunicationCareAccessClient(@Qualifier("clinicalCommunicationRestClient") RestClient client,
            ClinicalSecurityProperties properties, Clock clock) {
        this.client = client; this.cookieName = properties.accessTokenCookieName(); this.clock = clock;
    }

    public Instant requireCare(UUID organisationId, UUID actorUserId, UUID patientRegistrationId,
            String accessToken, String requestId) {
        try {
            var decision = client.get().uri(uri -> uri.path("/api/v1/sharing/care-access-decisions")
                    .queryParam("patientRegistrationId", patientRegistrationId).build())
                    .header(HttpHeaders.COOKIE, cookieName + "=" + accessToken)
                    .header("X-Request-ID", requestId).retrieve().body(CareAccessDecisionResource.class);
            if (decision == null || decision.allowed() == null) throw new SharingContextUnavailableException();
            if (!decision.allowed()) throw new ConsultationNotFoundException();
            if (!organisationId.equals(decision.organisationId())
                    || !actorUserId.equals(decision.doctorUserId())
                    || !patientRegistrationId.equals(decision.patientRegistrationId())
                    || decision.grantId() == null || decision.referralId() == null || decision.validUntil() == null) {
                throw new SharingContextUnavailableException();
            }
            if (!clock.instant().isBefore(decision.validUntil())) throw new ConsultationNotFoundException();
            return decision.validUntil();
        }
        catch (HttpClientErrorException.NotFound | HttpClientErrorException.Unauthorized
                | HttpClientErrorException.Forbidden denied) { throw new ConsultationNotFoundException(); }
        catch (RestClientException unavailable) { throw new SharingContextUnavailableException(); }
    }
}
