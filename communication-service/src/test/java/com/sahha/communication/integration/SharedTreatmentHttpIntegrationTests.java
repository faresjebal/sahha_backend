package com.sahha.communication.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;
import com.sahha.communication.exception.CommunicationContextUnavailableException;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.ReferralCareParticipationRepository;
import com.sahha.communication.repository.ReferralRequestRepository;
import com.sahha.communication.service.referralservice.ReferralService;

@SpringBootTest
@AutoConfigureMockMvc(printOnlyOnFailure = false, print = org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint.NONE)
@Transactional
class SharedTreatmentHttpIntegrationTests {
    private static final String SENDER = "care.sender.token", RECIPIENT = "care.recipient.token", OTHER = "care.other.token";
    private static final String CSRF = "synthetic-care-csrf";
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    private final UUID org = UUID.randomUUID(), patient = UUID.randomUUID(), sender = UUID.randomUUID(),
            recipient = UUID.randomUUID(), other = UUID.randomUUID(), senderMember = UUID.randomUUID(),
            recipientMember = UUID.randomUUID(), otherMember = UUID.randomUUID();
    private final JsonMapper mapper = JsonMapper.builder().build();
    @Autowired MockMvc mvc;
    @Autowired ReferralCareParticipationRepository care;
    @Autowired ReferralRequestRepository referrals;
    @Autowired ReferralService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean OrganisationCollaborationClient directory;
    @MockitoBean SchedulingPatientContextClient scheduling;
    @MockitoBean Clock clock;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        token(SENDER, org, sender, "DOCTOR"); token(RECIPIENT, org, recipient, "DOCTOR");
        token(OTHER, org, other, "DOCTOR");
        when(directory.resolve(eq(org), any(UUID.class), anyString())).thenAnswer(call -> {
            UUID user = call.getArgument(1);
            UUID member = user.equals(sender) ? senderMember : user.equals(recipient) ? recipientMember : otherMember;
            return new CollaborationDoctorResource(member, org, user, "Synthetic doctor", 1);
        });
    }

    @Test void legacyRequestsStaySelectedOnlyAndCannotBeUpgradedByRetry() throws Exception {
        var command = command(null); command.put("selectedItems", selections());
        var sent = create(command).andExpect(status().isCreated()).andExpect(jsonPath("$.referralType").value("SECOND_OPINION"));
        var active = accept(json(sent));
        assertEquals(0, care.count());
        decision(SENDER, patient, false); decision(RECIPIENT, patient, false);
        command.put("referralType", "SHARED_TREATMENT");
        create(command).andExpect(status().isConflict());
        assertEquals("SECOND_OPINION", active.get("referralType").asText());
    }

    @Test void sharedTreatmentCreatesBothParticipantsOnlyOnAcceptanceAndRevokesTogether() throws Exception {
        var command = command("SHARED_TREATMENT"); command.put("sendImmediately", false);
        var draft = json(create(command).andExpect(status().isCreated()));
        assertEquals(0, care.count());
        decision(SENDER, patient, false); decision(RECIPIENT, patient, false);
        var sent = action(draft, "send", SENDER, null);
        assertEquals(0, care.count()); decision(RECIPIENT, patient, false);
        var active = accept(sent);
        assertEquals(2, care.count());
        assertEquals(java.util.Set.of(sender, recipient), care.findAll().stream()
                .map(value -> value.getDoctorUserId()).collect(java.util.stream.Collectors.toSet()));
        for (String actor : List.of(SENDER, RECIPIENT)) {
            decision(actor, patient, true).andExpect(jsonPath("$.organisationId").value(org.toString()))
                    .andExpect(jsonPath("$.referralId").value(active.get("id").asText()))
                    .andExpect(jsonPath("$.grantId").value(active.get("sharingGrantId").asText()));
        }
        action(active, "revoke", SENDER, "Synthetic consent withdrawal");
        decision(SENDER, patient, false); decision(RECIPIENT, patient, false);
        assertEquals(2, care.count(), "Ended authority retains historical participation");
        assertEquals(2, jdbc.queryForObject("select count(*) from communication_audit_event where event_type = 'SHARED_CARE_PARTICIPATION_STARTED'", Integer.class));
        assertTrue(jdbc.queryForObject("select count(*) from communication_audit_event where event_type = 'CARE_ACCESS_DENIED'", Integer.class) > 0);
    }

    @Test void sharedCreationRetriesAreBoundToTypeAndCannotDuplicateParticipation() throws Exception {
        var command = command("SHARED_TREATMENT");
        var sent = json(create(command).andExpect(status().isCreated()));
        var retry = json(create(command).andExpect(status().isCreated()));
        assertEquals(sent.get("id").asText(), retry.get("id").asText());
        var active = accept(sent);
        create(command).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/referrals/{id}/accept", active.get("id").asText())
                .cookie(access(RECIPIENT), csrf()).header("X-XSRF-TOKEN", CSRF)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                        Map.of("expectedVersion", active.get("version").asLong()))))
                .andExpect(status().isConflict());
        assertEquals(2, care.count());
    }

    @Test void completedAndExpiredReferralsStopBothDoctorsWithoutWaitingForWorker() throws Exception {
        var active = accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        action(active, "complete", RECIPIENT, null);
        decision(SENDER, patient, false); decision(RECIPIENT, patient, false);
        active = accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        decision(RECIPIENT, patient, true);
        when(clock.instant()).thenReturn(now.plusSeconds(3600));
        decision(SENDER, patient, false); decision(RECIPIENT, patient, false);
        assertEquals(1, service.expireDue());
        assertEquals("EXPIRED", referrals.findById(UUID.fromString(active.get("id").asText())).orElseThrow().getStatus().name());
        assertEquals(4, care.count());
    }

    @Test void independentReferralSurvivesRevocationOfAnotherForSamePatient() throws Exception {
        var first = accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        var second = accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        action(first, "revoke", SENDER, "One consent withdrawn");
        decision(RECIPIENT, patient, true).andExpect(jsonPath("$.referralId").value(second.get("id").asText()));
        action(second, "complete", RECIPIENT, null);
        decision(RECIPIENT, patient, false);
    }

    @Test void wrongPatientOrganisationUnrelatedDoctorAndAdministrativeRolesAreDenied() throws Exception {
        accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        decision(RECIPIENT, UUID.randomUUID(), false); decision(OTHER, patient, false);
        UUID anotherOrg = UUID.randomUUID(); token("care.cross-org.token", anotherOrg, recipient, "DOCTOR");
        when(directory.resolve(anotherOrg, recipient, "care.cross-org.token")).thenReturn(
                new CollaborationDoctorResource(recipientMember, anotherOrg, recipient, "Synthetic doctor", 1));
        decision("care.cross-org.token", patient, false);
        for (String role : List.of("RECEPTIONIST", "ORGANIZATION_ADMIN")) {
            token("care.admin.token", org, recipient, role);
            mvc.perform(get("/api/v1/sharing/care-access-decisions").cookie(access("care.admin.token"))
                    .param("patientRegistrationId", patient.toString())).andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/sharing/care-access-decisions").param("patientRegistrationId", patient.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test void membershipRemovalReplacementAndDirectoryOutageCannotResurrectAccess() throws Exception {
        accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        when(directory.resolve(org, sender, RECIPIENT)).thenReturn(
                new CollaborationDoctorResource(UUID.randomUUID(), org, sender, "Rejoined doctor", 1));
        decision(RECIPIENT, patient, false);
        when(directory.resolve(org, sender, RECIPIENT)).thenThrow(new ConversationNotFoundException());
        decision(RECIPIENT, patient, false);
        when(directory.resolve(org, recipient, SENDER)).thenReturn(
                new CollaborationDoctorResource(UUID.randomUUID(), org, recipient, "Rejoined recipient", 1));
        decision(SENDER, patient, false);
        when(directory.resolve(org, recipient, RECIPIENT)).thenReturn(
                new CollaborationDoctorResource(UUID.randomUUID(), org, recipient, "Rejoined recipient", 1));
        decision(RECIPIENT, patient, false);
        when(directory.resolve(org, recipient, RECIPIENT)).thenThrow(new CommunicationContextUnavailableException());
        mvc.perform(get("/api/v1/sharing/care-access-decisions").cookie(access(RECIPIENT))
                .param("patientRegistrationId", patient.toString())).andExpect(status().isServiceUnavailable());
    }

    @Test void sharedTreatmentStillRequiresPatientAuthorityConsentAndValidType() throws Exception {
        var command = command("SHARED_TREATMENT");
        for (String required : List.of("consentType", "consentEvidenceReference", "consentRecordedAt", "purpose", "selectedItems")) {
            var invalid = new HashMap<>(command); invalid.remove(required);
            create(invalid).andExpect(status().isBadRequest());
        }
        var invalidType = new HashMap<>(command); invalidType.put("referralType", "FULL_ACCESS");
        create(invalidType).andExpect(status().isBadRequest());
        create(command(null)).andExpect(status().isBadRequest()); // Second opinions require a selection.
        doThrow(new ConversationNotFoundException()).when(scheduling).requireMentionable(org, patient, sender, SENDER);
        create(command).andExpect(status().isNotFound());
        assertEquals(0, care.count());
    }

    @Test void rejectedSharedTreatmentNeverCreatesCareParticipation() throws Exception {
        var sent = json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated()));
        action(sent, "reject", RECIPIENT, "Synthetic recipient declined");
        assertEquals(0, care.count()); decision(RECIPIENT, patient, false);
    }

    @Test void sharedCareDoesNotTurnSelectedDecisionIntoAnUnrestrictedResourceDecision() throws Exception {
        var command = command("SHARED_TREATMENT"); command.put("selectedItems", selections());
        accept(json(create(command).andExpect(status().isCreated())));
        mvc.perform(get("/api/v1/sharing/access-decisions").cookie(access(RECIPIENT))
                .param("patientRegistrationId", patient.toString()).param("resourceType", "CONSULTATION")
                .param("resourceId", UUID.randomUUID().toString()).param("resourceOwnerUserId", sender.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        decision(RECIPIENT, patient, true);
    }

    @Test void databaseRejectsScopeChangesAndParticipationMutationOrForeignDoctors() throws Exception {
        var active = accept(json(create(command("SHARED_TREATMENT")).andExpect(status().isCreated())));
        UUID referralId = UUID.fromString(active.get("id").asText());
        UUID grantId = UUID.fromString(active.get("sharingGrantId").asText());
        rejectsSql("update referral_request set referral_type = 'SECOND_OPINION' where id = ?", referralId);
        rejectsSql("update referral_care_participation set doctor_user_id = ? where grant_id = ?", other, grantId);
        rejectsSql("delete from referral_care_participation where grant_id = ?", grantId);
        rejectsSql("insert into referral_care_participation (id, grant_id, doctor_user_id, doctor_membership_id, started_at) "
                + "select ?, id, ?, ?, valid_from from referral_sharing_grant where id = ?",
                UUID.randomUUID(), other, otherMember, grantId);
        assertEquals(2, care.count());
        decision(SENDER, patient, true); decision(RECIPIENT, patient, true);
    }

    private void rejectsSql(String sql, UUID... parameters) {
        jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try (var statement = connection.prepareStatement(sql)) {
                for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
                assertThrows(java.sql.SQLException.class, statement::executeUpdate);
            }
            finally { connection.rollback(savepoint); connection.releaseSavepoint(savepoint); }
            return null;
        });
    }

    private Map<String, Object> command(String type) {
        var value = new HashMap<String, Object>();
        value.put("referralRequestId", UUID.randomUUID()); value.put("recipientUserId", recipient);
        value.put("patientRegistrationId", patient); value.put("reason", "Synthetic shared care");
        value.put("priority", "ROUTINE"); value.put("purpose", "Synthetic co-treatment");
        value.put("consentType", "RECORDED_WRITTEN"); value.put("consentEvidenceReference", "synthetic-care-consent");
        value.put("consentRecordedAt", now.minusSeconds(60).toString()); value.put("accessExpiresAt", now.plusSeconds(3600).toString());
        value.put("selectedItems", List.of()); value.put("sendImmediately", true);
        if (type != null) value.put("referralType", type);
        return value;
    }
    private List<Map<String, Object>> selections() {
        return List.of(Map.of("resourceType", "DIAGNOSIS", "resourceId", UUID.randomUUID()));
    }
    private org.springframework.test.web.servlet.ResultActions create(Map<String, Object> command) throws Exception {
        return mvc.perform(post("/api/v1/referrals").cookie(access(SENDER), csrf()).header("X-XSRF-TOKEN", CSRF)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(command)));
    }
    private JsonNode accept(JsonNode referral) throws Exception { return action(referral, "accept", RECIPIENT, null); }
    private JsonNode action(JsonNode referral, String action, String token, String reason) throws Exception {
        var body = new HashMap<String, Object>(); body.put("expectedVersion", referral.get("version").asLong());
        if (reason != null) body.put("reason", reason);
        return json(mvc.perform(post("/api/v1/referrals/{id}/{action}", referral.get("id").asText(), action)
                .cookie(access(token), csrf()).header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()));
    }
    private org.springframework.test.web.servlet.ResultActions decision(String token, UUID target, boolean allowed) throws Exception {
        var result = mvc.perform(get("/api/v1/sharing/care-access-decisions").cookie(access(token))
                .param("patientRegistrationId", target.toString())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.allowed").value(allowed));
        if (!allowed) result.andExpect(jsonPath("$.grantId").doesNotExist()).andExpect(jsonPath("$.patientRegistrationId").doesNotExist());
        return result;
    }
    private JsonNode json(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private void token(String token, UUID organisation, UUID user, String role) {
        when(decoder.decode(token)).thenReturn(Jwt.withTokenValue(token).header("alg", "RS256")
                .subject(user.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(7200)).claim("sid", UUID.randomUUID().toString())
                .claim("cv", 1).claim("roles", List.of()).claim("org_id", organisation.toString())
                .claim("org_roles", List.of(role)).claim("token_type", "access").build());
    }
    private static Cookie access(String token) { return new Cookie("SAHHA_ACCESS_TOKEN", token); }
    private static Cookie csrf() { return new Cookie("XSRF-TOKEN", CSRF); }
}
