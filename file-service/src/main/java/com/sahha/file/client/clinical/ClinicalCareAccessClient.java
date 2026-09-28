package com.sahha.file.client.clinical;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.ClinicalContextUnavailableException;
import com.sahha.file.exception.FileResourceNotFoundException;

/** Clinical rechecks Communication's live grant on EVERY call. No positive cache or author fallback. */
@Component
public class ClinicalCareAccessClient {
    private final RestClient client;
    private final String cookie;
    private final Clock clock;
    public ClinicalCareAccessClient(@Qualifier("fileClinicalRestClient") RestClient client,
            FileSecurityProperties security, Clock clock) {
        this.client = client; this.cookie = security.accessTokenCookieName(); this.clock = clock;
    }
    public SharedCareAttachmentContextResource resolve(UUID org, UUID actor, UUID patient, UUID consultation,
            String token, String requestId) {
        try {
            var result = client.get().uri("/api/v1/clinical/shared-care/{patient}/consultations/{consultation}/attachment-context",
                            patient, consultation)
                    .header(HttpHeaders.COOKIE, cookie + "=" + token).header("X-Request-ID", requestId)
                    .retrieve().body(SharedCareAttachmentContextResource.class);
            if (result == null || !org.equals(result.organisationId()) || !actor.equals(result.actorUserId())
                    || !patient.equals(result.patientRegistrationId()) || !consultation.equals(result.consultationId())
                    || result.patientId() == null || !"FINALIZED".equals(result.consultationStatus())
                    || result.validUntil() == null) throw new ClinicalContextUnavailableException();
            if (!clock.instant().isBefore(result.validUntil())) throw new FileResourceNotFoundException();
            return result;
        }
        catch (HttpClientErrorException.NotFound | HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden denied) {
            throw new FileResourceNotFoundException();
        }
        catch (RestClientException unavailable) { throw new ClinicalContextUnavailableException(); }
    }
}
