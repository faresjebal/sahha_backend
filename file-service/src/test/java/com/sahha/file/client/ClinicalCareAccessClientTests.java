package com.sahha.file.client;

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
import com.sahha.file.client.clinical.ClinicalCareAccessClient;
import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.*;

class ClinicalCareAccessClientTests {
    private final UUID org = UUID.randomUUID(), actor = UUID.randomUUID(), patient = UUID.randomUUID(), consultation = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-09-21T10:00:00Z");
    private MockRestServiceServer server;
    private ClinicalCareAccessClient client;
    @BeforeEach void setup() {
        var builder = RestClient.builder().baseUrl("http://clinical-service");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ClinicalCareAccessClient(builder.build(), new FileSecurityProperties("SAHHA_ACCESS_TOKEN",
                URI.create("http://localhost:8081/jwks"), "issuer", "audience", "XSRF-TOKEN", "X-XSRF-TOKEN", false),
                Clock.fixed(now, ZoneOffset.UTC));
    }
    @Test void forwardsOnlyCurrentCookieAndRequestIdAndNeverCachesAuthority() {
        for (int index = 0; index < 2; index++) server.expect(requestTo(url())).andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.COOKIE, "SAHHA_ACCESS_TOKEN=synthetic.token"))
                .andExpect(header("X-Request-ID", "care-files"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(json(context()), MediaType.APPLICATION_JSON));
        assertEquals(consultation, resolve().consultationId()); resolve(); server.verify();
    }
    @Test void rejectsEveryIdentityMismatchDraftMissingFieldAndMalformedResponse() {
        for (String field : List.of("organisationId", "actorUserId", "patientRegistrationId", "consultationId",
                "patientId", "consultationStatus", "validUntil")) {
            for (boolean missing : List.of(false, true)) {
                server.reset(); var value = context();
                if (missing) value.remove(field);
                else value.put(field, field.equals("consultationStatus") ? "DRAFT"
                        : field.equals("validUntil") ? "invalid" : field.equals("patientId") ? "" : UUID.randomUUID().toString());
                server.expect(requestTo(url())).andRespond(withSuccess(json(value), MediaType.APPLICATION_JSON));
                assertThrows(ClinicalContextUnavailableException.class, this::resolve); server.verify();
            }
        }
        server.reset(); server.expect(requestTo(url())).andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertThrows(ClinicalContextUnavailableException.class, this::resolve);
    }
    @Test void mapsDenialExpiryAndDependencyFailureWithoutAnyFallback() {
        for (var status : List.of(HttpStatus.NOT_FOUND, HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN,
                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.INTERNAL_SERVER_ERROR)) {
            server.reset(); server.expect(requestTo(url())).andRespond(withStatus(status));
            if (status.is4xxClientError()) assertThrows(FileResourceNotFoundException.class, this::resolve);
            else assertThrows(ClinicalContextUnavailableException.class, this::resolve);
        }
        server.reset(); var expired = context(); expired.put("validUntil", now.toString());
        server.expect(requestTo(url())).andRespond(withSuccess(json(expired), MediaType.APPLICATION_JSON));
        assertThrows(FileResourceNotFoundException.class, this::resolve);
        server.reset(); server.expect(requestTo(url())).andRespond(withException(new java.net.SocketTimeoutException("synthetic timeout")));
        assertThrows(ClinicalContextUnavailableException.class, this::resolve);
    }
    private com.sahha.file.client.clinical.SharedCareAttachmentContextResource resolve() {
        return client.resolve(org, actor, patient, consultation, "synthetic.token", "care-files");
    }
    private String url() { return "http://clinical-service/api/v1/clinical/shared-care/" + patient + "/consultations/" + consultation + "/attachment-context"; }
    private Map<String, Object> context() {
        return new HashMap<>(Map.of("organisationId", org, "actorUserId", actor, "patientRegistrationId", patient,
                "consultationId", consultation, "patientId", UUID.randomUUID(), "consultationStatus", "FINALIZED", "validUntil", now.plusSeconds(30).toString()));
    }
    private String json(Object value) { return JsonMapper.builder().build().writeValueAsString(value); }
}
