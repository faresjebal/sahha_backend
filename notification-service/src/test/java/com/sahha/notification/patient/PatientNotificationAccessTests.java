package com.sahha.notification.patient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import com.sahha.notification.config.NotificationSecurityProperties;
import com.sahha.notification.exception.*;

class PatientNotificationAccessTests {
    private final UUID registration = UUID.randomUUID(), user = UUID.randomUUID(), patient = UUID.randomUUID(), org = UUID.randomUUID();
    private final Jwt jwt = Jwt.withTokenValue("patient.access.token").header("alg", "RS256").subject(user.toString()).build();
    private MockRestServiceServer server;
    private PatientNotificationAccess access;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://patient-service");
        server = MockRestServiceServer.bindTo(builder).build();
        access = new PatientNotificationAccess(builder.build(), new NotificationSecurityProperties("SAHHA_ACCESS_TOKEN",
                java.net.URI.create("http://localhost/jwks"), "issuer", "audience", "XSRF-TOKEN", "X-XSRF-TOKEN", false,
                java.util.List.of("http://localhost:5173")));
    }
    @Test void forwardsSessionAndAcceptsOnlyVerifiedMinimalContext() {
        expectContext(user, registration, "ACTIVE");
        assertEquals(new PatientNotificationContext(registration, patient, org, "ACTIVE", user), access.requireOwnRegistration(registration, jwt));
        server.verify();
    }
    @Test void mismatchedAccountFailsClosed() {
        expectContext(UUID.randomUUID(), registration, "ACTIVE");
        assertThrows(NotificationNotFoundException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    @Test void mismatchedRegistrationFailsClosed() {
        expectContext(user, UUID.randomUUID(), "ACTIVE");
        assertThrows(NotificationNotFoundException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    @Test void inactiveRegistrationFailsClosed() {
        expectContext(user, registration, "INACTIVE");
        assertThrows(NotificationNotFoundException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    @Test void dependencyUnavailableFailsClosed() {
        server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(PatientNotificationContextUnavailableException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    @Test void unlinkedRegistrationRemainsNotFound() {
        server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.NOT_FOUND));
        assertThrows(NotificationNotFoundException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    @Test void expiredOrForbiddenSessionCannotUsePatientContext() {
        server.expect(requestTo(url())).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(NotificationAccessDeniedException.class, () -> access.requireOwnRegistration(registration, jwt));
    }
    private String url() { return "http://patient-service/api/v1/patients/me/registrations/" + registration + "/scheduling-context"; }
    private void expectContext(UUID actor, UUID id, String status) {
        String body = new ObjectMapper().writeValueAsString(Map.of("registrationId", id, "patientId", patient,
                "organisationId", org, "authUserId", actor, "registrationStatus", status));
        server.expect(requestTo(url())).andExpect(header("Cookie", "SAHHA_ACCESS_TOKEN=patient.access.token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
}
