package com.sahha.communication.client.clinical;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.communication.config.ClinicalClientProperties;
import com.sahha.communication.config.CommunicationSecurityProperties;
import com.sahha.communication.exception.CommunicationContextUnavailableException;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;
import com.sahha.communication.service.conversationservice.CollaborationPatientContextService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ClinicalCollaborationSourceClientTests {
    final UUID organisation = UUID.randomUUID(), patient = UUID.randomUUID(), actor = UUID.randomUUID(), source = UUID.randomUUID();
    final RestClient.Builder builder = RestClient.builder().baseUrl("http://clinical.test");
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    final ClinicalCollaborationSourceClient client = new ClinicalCollaborationSourceClient(builder.build(),
            new CommunicationSecurityProperties("DEMO_ACCESS", URI.create("http://auth.test/jwks"),
                    "http://auth.test", "sahha-api", "DEMO_CSRF", "X-XSRF-TOKEN", false));
    Map<String, Object> metadata() {
        return new HashMap<>(Map.of("consultationId", source, "organisationId", organisation,
                "patientRegistrationId", patient, "patientId", UUID.randomUUID(), "doctorUserId", actor,
                "consultationStatus", "FINALIZED", "consultationVersion", 2));
    }
    void require() { client.requireFinalizedSource(organisation, patient, actor, source, "synthetic.token"); }
    String route() { return "http://clinical.test/api/v1/internal/clinical/consultations/" + source + "/attachment-context"; }

    @Test void validatesMetadataAndForwardsOnlyAccessCookieAndCorrelation() {
        MDC.put("requestId", "synthetic-source-request");
        try {
            server.expect(requestTo(route())).andExpect(header("Cookie", "DEMO_ACCESS=synthetic.token"))
                    .andExpect(header("X-Request-ID", "synthetic-source-request"))
                    .andExpect(headerDoesNotExist("Authorization")).andExpect(headerDoesNotExist("X-XSRF-TOKEN"))
                    .andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(metadata()), MediaType.APPLICATION_JSON));
            require(); server.verify();
        } finally { MDC.clear(); }
    }
    @Test void mismatchedDraftOrIncompleteMetadataNeverGrantsContext() {
        for (String field : List.of("consultationId", "organisationId", "patientRegistrationId", "doctorUserId",
                "patientId", "consultationStatus", "consultationVersion")) {
            for (boolean absent : List.of(false, true)) {
                server.reset(); var value = metadata();
                if (absent) value.remove(field);
                else value.put(field, field.equals("consultationStatus") ? "DRAFT" : field.equals("consultationVersion") ? -1 : field.equals("patientId") ? null : UUID.randomUUID());
                server.expect(requestTo(route())).andRespond(withSuccess(JsonMapper.builder().build().writeValueAsString(value), MediaType.APPLICATION_JSON));
                assertThrows(ConversationNotFoundException.class, this::require); server.verify();
            }
        }
    }
    @Test void denialsAndOutagesFailClosedWithoutEchoingDownstreamBodies() {
        for (HttpStatus status : List.of(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND,
                HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.FOUND)) {
            server.reset();
            server.expect(requestTo(route())).andRespond(withStatus(status).body("private downstream detail").contentType(MediaType.TEXT_PLAIN));
            var failure = assertThrows(RuntimeException.class, this::require);
            assertTrue(status.is4xxClientError() ? failure instanceof ConversationNotFoundException
                    : failure instanceof CommunicationContextUnavailableException || failure instanceof ConversationNotFoundException);
            assertFalse(failure.toString().contains("private downstream")); server.verify();
        }
    }
    @Test void explicitSourceNeverFallsBackToScheduling() {
        var scheduling = mock(SchedulingPatientContextClient.class);
        var clinical = mock(ClinicalCollaborationSourceClient.class);
        var contexts = new CollaborationPatientContextService(scheduling, clinical);
        contexts.requireMentionable(organisation, patient, actor, "token", null);
        verify(scheduling).requireMentionable(organisation, patient, actor, "token"); verifyNoInteractions(clinical);
        clearInvocations(scheduling);
        for (RuntimeException denied : List.of(new ConversationNotFoundException(), new CommunicationContextUnavailableException())) {
            doThrow(denied).when(clinical).requireFinalizedSource(organisation, patient, actor, source, "token");
            assertThrows(denied.getClass(), () -> contexts.requireMentionable(organisation, patient, actor, "token", source));
            verifyNoInteractions(scheduling);
        }
    }
    @Test void configurationRefusesNonServiceOrigins() {
        for (String value : List.of("file:///tmp/test", "http://user:password@clinical.test", "http://clinical.test/path",
                "http://clinical.test?q=secret", "http://clinical.test#fragment")) {
            assertThrows(IllegalArgumentException.class, () -> new ClinicalClientProperties(URI.create(value)));
        }
        assertEquals("clinical-service", new ClinicalClientProperties(URI.create("http://clinical-service")).baseUrl().getHost());
    }
}
