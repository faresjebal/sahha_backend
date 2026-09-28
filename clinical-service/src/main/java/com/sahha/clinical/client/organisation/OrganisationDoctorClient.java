package com.sahha.clinical.client.organisation;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.OrganisationContextUnavailableException;

@Component
public class OrganisationDoctorClient {
    private final RestClient client;
    private final String cookieName;
    public OrganisationDoctorClient(@Qualifier("clinicalOrganisationRestClient") RestClient client,
            ClinicalSecurityProperties properties) {
        this.client = client;
        this.cookieName = properties.accessTokenCookieName();
    }
    public UUID requireCurrentMembership(UUID organisationId, UUID actorId, String token, String requestId) {
        try {
            var request = client.get()
                    .uri("/api/v1/organisations/{organisationId}/collaboration-doctors/{userId}", organisationId, actorId)
                    .header(HttpHeaders.COOKIE, cookieName + "=" + token);
            if (requestId != null && !requestId.isBlank()) request.header("X-Request-ID", requestId);
            DoctorIdentity doctor = request.retrieve().body(DoctorIdentity.class);
            if (doctor == null || doctor.membershipId() == null
                    || !organisationId.equals(doctor.organisationId()) || !actorId.equals(doctor.userId())
                    || doctor.membershipVersion() == null || doctor.membershipVersion() < 0) {
                throw new OrganisationContextUnavailableException();
            }
            return doctor.membershipId();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden
                | HttpClientErrorException.NotFound denied) {
            throw new ClinicalAccessDeniedException();
        } catch (RestClientException unavailable) {
            throw new OrganisationContextUnavailableException();
        }
    }
    public record DoctorIdentity(UUID membershipId, UUID organisationId, UUID userId, Long membershipVersion) {}
}
