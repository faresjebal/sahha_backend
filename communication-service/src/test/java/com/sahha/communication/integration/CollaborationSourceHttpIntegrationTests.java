package com.sahha.communication.integration;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.communication.client.clinical.ClinicalCollaborationSourceClient;
import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.exception.CommunicationContextUnavailableException;
import com.sahha.communication.repository.ConversationThreadRepository;
import com.sahha.communication.repository.ReferralRequestRepository;
import com.sahha.communication.repository.ReferralSharingGrantRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class CollaborationSourceHttpIntegrationTests {
    static final String TOKEN = "source.author.token", RECIPIENT = "source.recipient.token", CSRF = "synthetic-csrf";
    final UUID org = UUID.randomUUID(), patient = UUID.randomUUID(), author = UUID.randomUUID(),
            membership = UUID.randomUUID(), recipient = UUID.randomUUID(), recipientMembership = UUID.randomUUID(), source = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ConversationThreadRepository conversations;
    @Autowired ReferralRequestRepository referrals;
    @Autowired ReferralSharingGrantRepository grants;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationCollaborationClient directory;
    @MockitoBean SchedulingPatientContextClient scheduling;
    @MockitoBean ClinicalCollaborationSourceClient clinical;

    @BeforeEach void setup() {
        for (var entry : Map.of(TOKEN, author, RECIPIENT, recipient).entrySet()) {
            when(decoder.decode(entry.getKey())).thenReturn(Jwt.withTokenValue(entry.getKey()).header("alg", "RS256")
                    .subject(entry.getValue().toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                    .claim("sid", UUID.randomUUID().toString()).claim("cv", 1).claim("roles", List.of())
                    .claim("org_id", org.toString()).claim("org_roles", List.of("DOCTOR")).claim("token_type", "access").build());
        }
        when(directory.resolve(eq(org), any(UUID.class), anyString())).thenAnswer(call -> {
            UUID user = call.getArgument(1);
            return new CollaborationDoctorResource(user.equals(author) ? membership : recipientMembership,
                    org, user, "Synthetic doctor", 1);
        });
        // A completed encounter is not active care: the old Scheduling gate denies.
        doThrow(new ConversationNotFoundException()).when(scheduling).requireMentionable(any(), any(), any(), anyString());
    }
    Map<String,Object> conversation() {
        return new HashMap<>(Map.of("conversationRequestId", UUID.randomUUID(), "recipientUserId", recipient,
                "subject", "Synthetic finalised-source message", "patientRegistrationId", patient, "sourceConsultationId", source));
    }
    Map<String,Object> referral(boolean send) {
        var body = new HashMap<String,Object>();
        body.put("referralRequestId", UUID.randomUUID()); body.put("recipientUserId", recipient);
        body.put("patientRegistrationId", patient); body.put("sourceConsultationId", source);
        body.put("reason", "Synthetic second opinion"); body.put("priority", "ROUTINE"); body.put("purpose", "Synthetic selected sharing");
        body.put("consentType", "RECORDED_WRITTEN"); body.put("consentEvidenceReference", "synthetic-only-evidence");
        body.put("consentRecordedAt", Instant.now().minusSeconds(10).toString()); body.put("accessExpiresAt", Instant.now().plusSeconds(3600).toString());
        body.put("selectedItems", List.of(Map.of("resourceType", "DIAGNOSIS", "resourceId", UUID.randomUUID())));
        body.put("sendImmediately", send); return body;
    }
    ResultActions command(String route, Object body) throws Exception {
        return mvc.perform(post(route).cookie(new Cookie("SAHHA_ACCESS_TOKEN", TOKEN), new Cookie("XSRF-TOKEN", CSRF))
                .header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON)
                .content(JsonMapper.builder().build().writeValueAsString(body)));
    }
    String created(String route, Map<String,Object> body) throws Exception {
        String json = command(route, body).andExpect(status().isCreated()).andExpect(jsonPath("$.sourceConsultationId").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(json).get("id").asText();
    }
    @Test void completedEncounterCanAnchorAMentionAndDraftSendWithoutGrantingAccess() throws Exception {
        var mention = conversation(); String conversationId = created("/api/v1/conversations", mention);
        assertEquals(source, conversations.findById(UUID.fromString(conversationId)).orElseThrow().getSourceConsultationId());
        mvc.perform(get("/api/v1/conversations/" + conversationId).cookie(new Cookie("SAHHA_ACCESS_TOKEN", RECIPIENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.patientAccessGranted").value(false))
                .andExpect(jsonPath("$.sourceConsultationId").doesNotExist());
        command("/api/v1/conversations", mention).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(conversationId));
        var draft = referral(false); String id = created("/api/v1/referrals", draft);
        var stored = referrals.findById(UUID.fromString(id)).orElseThrow();
        assertEquals(source, stored.getSourceConsultationId()); assertEquals(0, grants.count());
        command("/api/v1/referrals/" + id + "/send", Map.of("expectedVersion", stored.getVersion()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.sharingGrantId").isEmpty());
        mvc.perform(get("/api/v1/referrals/" + id).cookie(new Cookie("SAHHA_ACCESS_TOKEN", RECIPIENT)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sourceConsultationId").doesNotExist());
        assertEquals(0, grants.count()); verifyNoInteractions(scheduling);
        verify(clinical, times(4)).requireFinalizedSource(org, patient, author, source, TOKEN);
    }
    @Test void differentSourceCannotReuseACommandOrCreateExtraRows() throws Exception {
        var mention = conversation(); created("/api/v1/conversations", mention);
        mention.put("sourceConsultationId", UUID.randomUUID()); command("/api/v1/conversations", mention).andExpect(status().isConflict());
        var request = referral(true); String id = created("/api/v1/referrals", request);
        command("/api/v1/referrals", request).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        request.put("sourceConsultationId", UUID.randomUUID()); command("/api/v1/referrals", request).andExpect(status().isConflict());
        assertEquals(1, conversations.count()); assertEquals(1, referrals.count()); assertEquals(0, grants.count());
    }
    @Test void deniedOrUnavailableSourceCreatesNothingAndDoesNotFallback() throws Exception {
        for (RuntimeException failure : List.of(new ConversationNotFoundException(), new CommunicationContextUnavailableException())) {
            doThrow(failure).when(clinical).requireFinalizedSource(org, patient, author, source, TOKEN);
            int expected = failure instanceof ConversationNotFoundException ? 404 : 503;
            command("/api/v1/conversations", conversation()).andExpect(status().is(expected));
            command("/api/v1/referrals", referral(true)).andExpect(status().is(expected));
        }
        assertEquals(0, conversations.count()); assertEquals(0, referrals.count()); verifyNoInteractions(scheduling);
    }
    @Test void sourceWithoutPatientAndUnanchoredCompletedCareStayDenied() throws Exception {
        var mention = conversation(); mention.remove("patientRegistrationId");
        command("/api/v1/conversations", mention).andExpect(status().isBadRequest());
        mention = conversation(); mention.remove("sourceConsultationId"); command("/api/v1/conversations", mention).andExpect(status().isNotFound());
        var request = referral(true); request.remove("sourceConsultationId"); command("/api/v1/referrals", request).andExpect(status().isNotFound());
        verifyNoInteractions(clinical); assertEquals(0, conversations.count()); assertEquals(0, referrals.count());
    }
    @Test void draftSendRevalidatesItsStoredSource() throws Exception {
        String id = created("/api/v1/referrals", referral(false));
        var stored = referrals.findById(UUID.fromString(id)).orElseThrow();
        doThrow(new ConversationNotFoundException()).when(clinical).requireFinalizedSource(org, patient, author, source, TOKEN);
        command("/api/v1/referrals/" + id + "/send", Map.of("expectedVersion", stored.getVersion())).andExpect(status().isNotFound());
        assertEquals("DRAFT", referrals.findById(UUID.fromString(id)).orElseThrow().getStatus().name());
        assertEquals(0, grants.count()); verifyNoInteractions(scheduling);
    }
    @Test void databaseRejectsConversationSourceMutation() throws Exception {
        UUID id = UUID.fromString(created("/api/v1/conversations", conversation())); conversations.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update communication_test.conversation_thread set source_consultation_id = null where id = ?", id));
    }
    @Test void databaseRejectsReferralSourceMutation() throws Exception {
        UUID id = UUID.fromString(created("/api/v1/referrals", referral(false))); referrals.flush();
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("update communication_test.referral_request set source_consultation_id = null where id = ?", id));
    }
}
