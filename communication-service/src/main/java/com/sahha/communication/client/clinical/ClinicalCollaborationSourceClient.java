package com.sahha.communication.client.clinical;

import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.sahha.communication.config.CommunicationSecurityProperties;
import com.sahha.communication.exception.CommunicationContextUnavailableException;
import com.sahha.communication.exception.ConversationNotFoundException;

/** Author-attested metadata only. This does not request or grant clinical content. */
@Component
public class ClinicalCollaborationSourceClient {
    private final RestClient client;
    private final String accessCookieName;

    public ClinicalCollaborationSourceClient(@Qualifier("communicationClinicalRestClient") RestClient client,
            CommunicationSecurityProperties properties) {
        this.client = client;
        this.accessCookieName = properties.accessTokenCookieName();
    }

    public void requireFinalizedSource(UUID organisationId, UUID patientRegistrationId,
            UUID actorId, UUID sourceConsultationId, String token) {
        String requestId = MDC.get("requestId");
        if (requestId == null || !requestId.matches("[A-Za-z0-9._-]{8,128}")) requestId = UUID.randomUUID().toString();
        try {
            // Clinical's existing internal author gate rechecks the original live
            // Doctor membership before returning this metadata, including after finalisation.
            Source value = client.get().uri("/api/v1/internal/clinical/consultations/{id}/attachment-context", sourceConsultationId)
                    .header(HttpHeaders.COOKIE, accessCookieName + "=" + token)
                    .header("X-Request-ID", requestId).retrieve().body(Source.class);
            if (value == null || !sourceConsultationId.equals(value.consultationId())
                    || !organisationId.equals(value.organisationId())
                    || !patientRegistrationId.equals(value.patientRegistrationId())
                    || !actorId.equals(value.doctorUserId()) || value.patientId() == null
                    || !"FINALIZED".equals(value.consultationStatus()) || value.consultationVersion() == null
                    || value.consultationVersion() < 0) throw new ConversationNotFoundException();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden
                | HttpClientErrorException.NotFound denied) {
            throw new ConversationNotFoundException();
        } catch (RestClientException unavailable) {
            throw new CommunicationContextUnavailableException();
        }
    }

    public record Source(UUID consultationId, UUID organisationId, UUID patientRegistrationId,
            UUID patientId, UUID doctorUserId, String consultationStatus, Long consultationVersion) { }
}
