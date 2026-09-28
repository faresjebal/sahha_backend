package com.sahha.clinical.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import java.net.URI;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.clinical.client.communication.CommunicationCareAccessClient;
import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.exception.ConsultationNotFoundException;
import com.sahha.clinical.exception.SharingContextUnavailableException;

class CommunicationCareAccessClientTests {
    private static final Instant NOW = Instant.parse("2026-09-21T10:00:00Z");
    private final UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), patient = UUID.randomUUID();
    private final JsonMapper mapper = JsonMapper.builder().build();
    private MockRestServiceServer server;
    private CommunicationCareAccessClient client;

    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://communication-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CommunicationCareAccessClient(builder.build(), new ClinicalSecurityProperties(
                "SAHHA_ACCESS_TOKEN", URI.create("http://localhost/jwks"), "http://localhost", "sahha-api",
                "XSRF-TOKEN", "X-XSRF-TOKEN", false), Clock.fixed(NOW, ZoneOffset.UTC));
    }
    @Test void bindsDecisionToCurrentDoctorOrganisationAndPatientAndRechecksEveryCall() {
        for (int index = 0; index < 2; index++) {
            server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                    .andExpect(header(HttpHeaders.COOKIE, "SAHHA_ACCESS_TOKEN=aaa.bbb.ccc"))
                    .andExpect(header("X-Request-ID", "care-read"))
                    .andRespond(withSuccess(mapper.writeValueAsString(allowed()), MediaType.APPLICATION_JSON));
        }
        assertEquals(NOW.plusSeconds(60), read()); assertEquals(NOW.plusSeconds(60), read()); server.verify();
    }
    @Test void missingOrMismatchedBindingsNeverGrantAccess() {
        for (String field : List.of("allowed", "organisationId", "doctorUserId", "patientRegistrationId", "grantId", "referralId", "validUntil")) {
            var response = allowed(); response.remove(field);
            malformed(mapper.writeValueAsString(response));
        }
        for (String field : List.of("organisationId", "doctorUserId", "patientRegistrationId")) {
            var response = allowed(); response.put(field, UUID.randomUUID());
            malformed(mapper.writeValueAsString(response));
        }
        for (String body : List.of("null", "{}", "not-json")) malformed(body);
    }
    @Test void expiryAndUpstreamDenialsConcealExistenceRatherThanReturningContent() {
        for (Instant until : List.of(NOW, NOW.minusSeconds(1))) {
            server.reset(); var response = allowed(); response.put("validUntil", until.toString());
            server.expect(requestTo(url())).andRespond(withSuccess(mapper.writeValueAsString(response), MediaType.APPLICATION_JSON));
            assertThrows(ConsultationNotFoundException.class, this::read); server.verify();
        }
        server.reset();
        server.expect(requestTo(url())).andRespond(withSuccess(mapper.writeValueAsString(Map.of("allowed", false)), MediaType.APPLICATION_JSON));
        assertThrows(ConsultationNotFoundException.class, this::read); server.verify();
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND)) {
            server.reset(); server.expect(requestTo(url())).andRespond(withStatus(status));
            assertThrows(ConsultationNotFoundException.class, this::read); server.verify();
        }
    }
    @Test void outageOrTimeoutFailsClosed() {
        server.expect(requestTo(url())).andRespond(withServerError());
        assertThrows(SharingContextUnavailableException.class, this::read); server.verify(); server.reset();
        server.expect(requestTo(url())).andRespond(withException(new java.io.IOException("Synthetic timeout")));
        assertThrows(SharingContextUnavailableException.class, this::read); server.verify();
    }
    private void malformed(String body) {
        server.reset(); server.expect(requestTo(url())).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThrows(SharingContextUnavailableException.class, this::read); server.verify();
    }
    private Map<String, Object> allowed() {
        return new HashMap<>(Map.of("allowed", true, "organisationId", org, "doctorUserId", actor,
                "patientRegistrationId", patient, "grantId", UUID.randomUUID(), "referralId", UUID.randomUUID(),
                "validUntil", NOW.plusSeconds(60).toString()));
    }
    private Instant read() { return client.requireCare(org, actor, patient, "aaa.bbb.ccc", "care-read"); }
    private String url() { return "http://communication-service/api/v1/sharing/care-access-decisions?patientRegistrationId=" + patient; }
}
