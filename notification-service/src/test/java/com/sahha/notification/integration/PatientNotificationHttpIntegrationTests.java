package com.sahha.notification.integration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.notification.patient.*;
import com.sahha.notification.event.*;
import com.sahha.notification.exception.NotificationNotFoundException;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientNotificationHttpIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired AppointmentNotificationService events;
    @Autowired PatientNotificationRepository repository;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean PatientNotificationAccess access;
    private PatientNotificationContext owner;
    private String base;
    private final Cookie token = new Cookie("SAHHA_ACCESS_TOKEN", "patient.http.token");
    private final Cookie csrf = new Cookie("XSRF-TOKEN", "patient-csrf");
    @BeforeEach void setup() {
        owner = new PatientNotificationContext(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ACTIVE", UUID.randomUUID());
        base = "/api/v1/notifications/patient/registrations/" + owner.registrationId();
        Jwt jwt = Jwt.withTokenValue(token.getValue()).header("alg", "RS256").subject(owner.authUserId().toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("sid", UUID.randomUUID().toString())
                .claim("cv", 1).claim("token_type", "access").claim("roles", List.of()).claim("org_roles", List.of()).build();
        when(decoder.decode(token.getValue())).thenReturn(jwt);
        when(access.requireOwnRegistration(any(), any())).thenThrow(new NotificationNotFoundException());
        doReturn(owner).when(access).requireOwnRegistration(eq(owner.registrationId()), any());
    }
    @Test void ownedInboxIsPrivateAndReadStateSurvivesRecovery() throws Exception {
        UUID id = seed(owner);
        seed(new PatientNotificationContext(UUID.randomUUID(), UUID.randomUUID(), owner.organisationId(), "ACTIVE", UUID.randomUUID()));
        seed(new PatientNotificationContext(UUID.randomUUID(), owner.patientId(), UUID.randomUUID(), "ACTIVE", owner.authUserId()));
        mvc.perform(get(base).cookie(token)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(id.toString()))
                .andExpect(jsonPath("$.items[0].patientId").doesNotExist()).andExpect(jsonPath("$.items[0].organisationId").doesNotExist());
        mvc.perform(get(base + "/unread-count").cookie(token)).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(post(base + "/" + id + "/read").cookie(token)).andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + id + "/read").cookie(token, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true));
        mvc.perform(get(base).cookie(token)).andExpect(jsonPath("$.items[0].read").value(true));
        mvc.perform(post(base + "/read-all").cookie(token, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(0));
    }
    @Test void unknownRegistrationAndForeignNotificationsDoNotLeakExistence() throws Exception {
        UUID id = seed(new PatientNotificationContext(UUID.randomUUID(), UUID.randomUUID(), owner.organisationId(), "ACTIVE", UUID.randomUUID()));
        mvc.perform(get("/api/v1/notifications/patient/registrations/" + UUID.randomUUID()).cookie(token)).andExpect(status().isNotFound());
        mvc.perform(post(base + "/" + id + "/read").cookie(token, csrf).header("X-XSRF-TOKEN", csrf.getValue())).andExpect(status().isNotFound());
    }
    @Test void ownershipIsRecheckedAndDependencyFailureFailsClosed() throws Exception {
        seed(owner);
        mvc.perform(get(base).cookie(token)).andExpect(status().isOk());
        doThrow(new NotificationNotFoundException()).when(access).requireOwnRegistration(eq(owner.registrationId()), any());
        mvc.perform(get(base).cookie(token)).andExpect(status().isNotFound());
        doThrow(new PatientNotificationContextUnavailableException()).when(access).requireOwnRegistration(eq(owner.registrationId()), any());
        mvc.perform(get(base + "/unread-count").cookie(token)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void authenticationPaginationAndCsrfRemainMandatory() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "?page=-1").cookie(token)).andExpect(status().isBadRequest());
        mvc.perform(get(base + "?size=101").cookie(token)).andExpect(status().isBadRequest());
        mvc.perform(post(base + "/read-all").cookie(token)).andExpect(status().isForbidden());
    }
    private UUID seed(PatientNotificationContext context) {
        Instant now = Instant.now();
        UUID eventId = UUID.randomUUID(), doctor = UUID.randomUUID();
        events.consume(new AppointmentEventV1(eventId, AppointmentEventType.APPOINTMENT_CONFIRMED, 1, now,
                UUID.randomUUID(), context.organisationId(), context.patientId(), doctor, doctor, "patient-http-test",
                AppointmentStatus.CONFIRMED, now.plusSeconds(3600), now.plusSeconds(5400), "UTC", "Synthetic room",
                1L, AppointmentStatus.REQUESTED, now.plusSeconds(3600)),
                new AppointmentEventSource("patient-http-" + eventId, 0, 0));
        return repository.list(context, 0, 20).items().getFirst().id();
    }
}
