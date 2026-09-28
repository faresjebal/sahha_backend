package com.sahha.file.client.communication;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.SharingContextUnavailableException;

@Component
public class CommunicationShareAccessClient {
    private final RestClient client;
    private final String cookieName;
    private final Clock clock;

    public CommunicationShareAccessClient(
            @Qualifier("fileCommunicationRestClient") RestClient client,
            FileSecurityProperties properties, Clock clock) {
        this.client = client;
        this.cookieName = properties.accessTokenCookieName();
        this.clock = clock;
    }

    public Instant requireGrant(UUID patientRegistrationId, String resourceType,
            UUID resourceId, UUID ownerUserId, UUID ownerMembershipId,
            String accessToken, String requestId) {
        try {
            var response = client.get().uri(uri -> {
                uri.path("/api/v1/sharing/access-decisions")
                        .queryParam("patientRegistrationId", patientRegistrationId)
                        .queryParam("resourceType", resourceType)
                        .queryParam("resourceId", resourceId)
                        .queryParam("resourceOwnerUserId", ownerUserId);
                if (ownerMembershipId != null) {
                    uri.queryParam("resourceOwnerMembershipId", ownerMembershipId);
                }
                return uri.build();
            }).header(HttpHeaders.COOKIE, cookieName + "=" + accessToken)
                    .header("X-Request-ID", requestId)
                    .retrieve().body(ShareAccessDecisionResource.class);
            if (response == null || response.allowed() == null) {
                throw new SharingContextUnavailableException();
            }
            if (!response.allowed()) throw new FileResourceNotFoundException();
            if (response.grantId() == null || response.referralId() == null
                    || response.validUntil() == null) {
                throw new SharingContextUnavailableException();
            }
            if (!clock.instant().isBefore(response.validUntil())) {
                throw new FileResourceNotFoundException();
            }
            return response.validUntil();
        }
        catch (HttpClientErrorException.NotFound | HttpClientErrorException.Unauthorized
                | HttpClientErrorException.Forbidden denied) {
            throw new FileResourceNotFoundException();
        }
        catch (RestClientException unavailable) {
            throw new SharingContextUnavailableException();
        }
    }
}
