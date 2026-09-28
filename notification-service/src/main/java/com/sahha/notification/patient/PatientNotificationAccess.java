package com.sahha.notification.patient;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import com.sahha.notification.config.NotificationSecurityProperties;
import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.exception.NotificationNotFoundException;

@Component
public class PatientNotificationAccess {
    private final RestClient client;
    private final String cookieName;
    public PatientNotificationAccess(@Qualifier("notificationPatientRestClient") RestClient client,
            NotificationSecurityProperties properties) {
        this.client = client;
        this.cookieName = properties.accessTokenCookieName();
    }

    public PatientNotificationContext requireOwnRegistration(UUID registrationId, Jwt jwt) {
        UUID user = UUID.fromString(jwt.getSubject());
        try {
            var context = client.get()
                    .uri("/api/v1/patients/me/registrations/{id}/scheduling-context", registrationId)
                    .header(HttpHeaders.COOKIE, cookieName + "=" + jwt.getTokenValue())
                    .retrieve().body(PatientNotificationContext.class);
            if (context == null || !registrationId.equals(context.registrationId())
                    || !user.equals(context.authUserId()) || context.patientId() == null
                    || context.organisationId() == null || !"ACTIVE".equals(context.registrationStatus())) {
                throw new NotificationNotFoundException();
            }
            return context;
        } catch (HttpClientErrorException.NotFound missing) {
            throw new NotificationNotFoundException();
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden denied) {
            throw new NotificationAccessDeniedException();
        } catch (RestClientException unavailable) {
            throw new PatientNotificationContextUnavailableException();
        }
    }
}
