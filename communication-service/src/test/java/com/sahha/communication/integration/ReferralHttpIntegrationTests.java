package com.sahha.communication.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
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
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;
import com.sahha.communication.repository.ReferralSharingGrantRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReferralHttpIntegrationTests {
	private static final String SENDER_TOKEN = "referral.sender.token";
	private static final String RECIPIENT_TOKEN = "referral.recipient.token";
	private static final String OTHER_TOKEN = "referral.other.token";
	private static final String RECEPTIONIST_TOKEN = "referral.receptionist.token";
	private static final String CSRF = "referral-csrf-token";

	@Autowired MockMvc mockMvc;
	@Autowired CommunicationAuditEventRepository auditRepository;
	@Autowired CommunicationOutboxEventRepository outboxRepository;
	@Autowired ReferralSharingGrantRepository grantRepository;
	@MockitoBean JwtDecoder jwtDecoder;
	@MockitoBean OrganisationCollaborationClient organisationClient;
	@MockitoBean SchedulingPatientContextClient patientContextClient;

	@Test
	void selectedSharingRequiresAcceptanceAndStopsImmediatelyAfterRevocation() throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID senderId = UUID.randomUUID();
		UUID senderMembershipId = UUID.randomUUID();
		UUID recipientId = UUID.randomUUID();
		UUID recipientMembershipId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();
		UUID patientRegistrationId = UUID.randomUUID();
		UUID consultationId = UUID.randomUUID();
		UUID unselectedConsultationId = UUID.randomUUID();
		workspace(SENDER_TOKEN, organisationId, senderId, List.of("DOCTOR"));
		workspace(RECIPIENT_TOKEN, organisationId, recipientId, List.of("DOCTOR"));
		workspace(OTHER_TOKEN, organisationId, otherId, List.of("DOCTOR"));
		workspace(RECEPTIONIST_TOKEN, organisationId, UUID.randomUUID(), List.of("RECEPTIONIST"));
		when(organisationClient.resolve(organisationId, senderId, SENDER_TOKEN))
				.thenReturn(doctor(organisationId, senderId, senderMembershipId, "Dr Synthetic Sender"));
		when(organisationClient.resolve(organisationId, recipientId, SENDER_TOKEN))
				.thenReturn(doctor(organisationId, recipientId, recipientMembershipId, "Dr Synthetic Recipient"));
		when(organisationClient.resolve(organisationId, recipientId, RECIPIENT_TOKEN))
				.thenReturn(doctor(organisationId, recipientId, recipientMembershipId, "Dr Synthetic Recipient"));
		when(organisationClient.resolve(organisationId, senderId, RECIPIENT_TOKEN))
				.thenReturn(doctor(organisationId, senderId, senderMembershipId, "Dr Synthetic Sender"));
		when(organisationClient.resolve(organisationId, otherId, OTHER_TOKEN))
				.thenReturn(doctor(organisationId, otherId, UUID.randomUUID(), "Dr Unrelated"));

		Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
		String createRequest = """
				{
				  "referralRequestId":"%s",
				  "recipientUserId":"%s",
				  "patientRegistrationId":"%s",
				  "reason":"Synthetic specialist assessment",
				  "priority":"ROUTINE",
				  "clinicalSummary":"Synthetic summary without real health data",
				  "purpose":"Specialist opinion for synthetic care",
				  "consentType":"RECORDED_VERBAL",
				  "consentEvidenceReference":"synthetic-consent-reference",
				  "consentRecordedAt":"%s",
				  "accessExpiresAt":"%s",
				  "selectedItems":[{"resourceType":"CONSULTATION","resourceId":"%s"}],
				  "sendImmediately":false
				}
				""".formatted(UUID.randomUUID(), recipientId, patientRegistrationId,
				now.minusSeconds(30), now.plus(7, ChronoUnit.DAYS), consultationId);

		String draftBody = mockMvc.perform(post("/api/v1/referrals")
					.cookie(access(SENDER_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(createRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.sharingGrantId").doesNotExist())
				.andExpect(jsonPath("$.selectedItems.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		JsonNode draft = JsonMapper.builder().build().readTree(draftBody);
		String referralId = draft.get("id").asText();
		long draftVersion = draft.get("version").asLong();

		// Draft message and consent evidence are private until the sender sends.
		mockMvc.perform(get("/api/v1/referrals/{id}", referralId).cookie(access(RECIPIENT_TOKEN)))
				.andExpect(status().isNotFound());
		for (String direction : List.of("ALL", "RECEIVED")) {
			mockMvc.perform(get("/api/v1/referrals").param("direction", direction).cookie(access(RECIPIENT_TOKEN)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		}

		mockMvc.perform(post("/api/v1/referrals")
					.cookie(access(SENDER_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(createRequest))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(referralId))
				.andExpect(jsonPath("$.version").value(draftVersion));

		mockMvc.perform(post("/api/v1/referrals/{id}/send", referralId)
					.cookie(access(SENDER_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":" + draftVersion + "}"))
				.andExpect(status().isForbidden());

		String sentBody = mockMvc.perform(post("/api/v1/referrals/{id}/send", referralId)
					.cookie(access(SENDER_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":" + draftVersion + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("SENT"))
				.andReturn().getResponse().getContentAsString();
		long sentVersion = JsonMapper.builder().build().readTree(sentBody).get("version").asLong();
		mockMvc.perform(get("/api/v1/referrals/{id}", referralId).cookie(access(RECIPIENT_TOKEN)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));

		String activeBody = mockMvc.perform(post("/api/v1/referrals/{id}/accept", referralId)
					.cookie(access(RECIPIENT_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"expectedVersion\":" + sentVersion + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.sharingGrantId").isNotEmpty())
				.andReturn().getResponse().getContentAsString();
		long activeVersion = JsonMapper.builder().build().readTree(activeBody).get("version").asLong();

		String selectedDecision = decision(patientRegistrationId, consultationId, senderId);
		mockMvc.perform(post("/api/v1/sharing/access-decisions")
					.cookie(access(RECIPIENT_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(selectedDecision))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allowed").value(true))
				.andExpect(jsonPath("$.referralId").value(referralId));
		mockMvc.perform(post("/api/v1/sharing/access-decisions")
					.cookie(access(RECIPIENT_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON)
					.content(decision(patientRegistrationId, unselectedConsultationId, senderId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allowed").value(false))
				.andExpect(jsonPath("$.grantId").doesNotExist());

		mockMvc.perform(get("/api/v1/referrals/{id}", referralId)
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/referrals").cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/referrals/{id}/revoke", referralId)
					.cookie(access(SENDER_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"expectedVersion":%d,"reason":"Synthetic referral withdrawn"}
							""".formatted(activeVersion)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REVOKED"));
		mockMvc.perform(post("/api/v1/sharing/access-decisions")
					.cookie(access(RECIPIENT_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(selectedDecision))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.allowed").value(false));

		org.junit.jupiter.api.Assertions.assertEquals(1, grantRepository.count());
		org.junit.jupiter.api.Assertions.assertTrue(auditRepository.count() >= 7);
		org.junit.jupiter.api.Assertions.assertEquals(3, outboxRepository.count());
		outboxRepository.findAll().forEach(event -> {
			org.junit.jupiter.api.Assertions.assertEquals(
					"sahha.communication.referrals.v1", event.getDestinationTopic());
			for (String forbidden : List.of("patientRegistrationId", "reason", "clinicalSummary",
					"purpose", "consentType", "consentEvidenceReference", "selectedItems",
					"resourceId")) {
				org.junit.jupiter.api.Assertions.assertFalse(event.getPayload().containsKey(forbidden));
			}
		});
	}

	@Test
	void immediateCreationAndRetryPersistOneSentReferralWithoutActivatingAccess() throws Exception {
		var request = creationFixture();
		String body = JsonMapper.builder().build().writeValueAsString(request);
		String response = mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SENT"))
				.andExpect(jsonPath("$.sharingGrantId").doesNotExist()).andExpect(jsonPath("$.selectedItems.length()").value(1))
				.andReturn().getResponse().getContentAsString();
		String id = JsonMapper.builder().build().readTree(response).get("id").asText();
		mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
		mockMvc.perform(get("/api/v1/referrals/{id}", id).cookie(access(RECIPIENT_TOKEN)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SENT"));
		mockMvc.perform(get("/api/v1/referrals").cookie(access(SENDER_TOKEN)))
				.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
		request.put("reason", "Changed synthetic request");
		mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON)
				.content(JsonMapper.builder().build().writeValueAsString(request)))
				.andExpect(status().isConflict());
		org.junit.jupiter.api.Assertions.assertEquals(0, grantRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(1, outboxRepository.count());
	}

	@Test
	void creationRejectsMissingConsentInvalidDatesEmptyOrDuplicateSelectionsAndMissingCsrf() throws Exception {
		var request = creationFixture();
		var mapper = JsonMapper.builder().build();
		String valid = mapper.writeValueAsString(request);
		mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN)).contentType(MediaType.APPLICATION_JSON).content(valid))
				.andExpect(status().isForbidden());
		var invalid = new java.util.ArrayList<java.util.Map<String, Object>>();
		for (String field : List.of("consentType", "consentEvidenceReference", "purpose", "selectedItems")) {
			var value = new java.util.HashMap<>(request); value.remove(field); invalid.add(value);
		}
		for (var change : List.of(java.util.Map.of("consentRecordedAt", Instant.now().plusSeconds(3600).toString()),
				java.util.Map.of("accessExpiresAt", Instant.now().minusSeconds(3600).toString()),
				java.util.Map.of("accessExpiresAt", Instant.now().plus(91, ChronoUnit.DAYS).toString()))) {
			var value = new java.util.HashMap<>(request); value.putAll(change); invalid.add(value);
		}
		Object item = ((List<?>) request.get("selectedItems")).getFirst();
		for (var items : List.of(List.of(), List.of(item, item), java.util.Collections.nCopies(51, item))) {
			var value = new java.util.HashMap<>(request); value.put("selectedItems", items); invalid.add(value);
		}
		for (var value : invalid) {
			mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(value)))
					.andExpect(status().isBadRequest());
		}
		org.junit.jupiter.api.Assertions.assertEquals(0, grantRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(0, outboxRepository.count());
	}

	@Test
	void withdrawingAnUnsentDraftNeverRevealsItToTheRecipient() throws Exception {
		var request = creationFixture(); request.put("sendImmediately", false);
		String response = mockMvc.perform(post("/api/v1/referrals").cookie(access(SENDER_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON)
				.content(JsonMapper.builder().build().writeValueAsString(request)))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		var draft = JsonMapper.builder().build().readTree(response);
		String id = draft.get("id").asText();
		mockMvc.perform(post("/api/v1/referrals/{id}/revoke", id).cookie(access(SENDER_TOKEN), csrf())
				.header("X-XSRF-TOKEN", CSRF).contentType(MediaType.APPLICATION_JSON)
				.content("{\"expectedVersion\":" + draft.get("version").asLong() + ",\"reason\":\"Draft withdrawn\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
		mockMvc.perform(get("/api/v1/referrals/{id}", id).cookie(access(RECIPIENT_TOKEN))).andExpect(status().isNotFound());
		for (String direction : List.of("ALL", "RECEIVED")) {
			mockMvc.perform(get("/api/v1/referrals").param("direction", direction).cookie(access(RECIPIENT_TOKEN)))
					.andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
		}
		org.junit.jupiter.api.Assertions.assertEquals(0, outboxRepository.count());
	}

	private java.util.Map<String, Object> creationFixture() {
		UUID organisation = UUID.randomUUID(), sender = UUID.randomUUID(), recipient = UUID.randomUUID();
		UUID senderMember = UUID.randomUUID(), recipientMember = UUID.randomUUID();
		workspace(SENDER_TOKEN, organisation, sender, List.of("DOCTOR"));
		workspace(RECIPIENT_TOKEN, organisation, recipient, List.of("DOCTOR"));
		when(organisationClient.resolve(organisation, sender, SENDER_TOKEN)).thenReturn(doctor(organisation, sender, senderMember, "Synthetic Sender"));
		when(organisationClient.resolve(organisation, recipient, SENDER_TOKEN)).thenReturn(doctor(organisation, recipient, recipientMember, "Synthetic Recipient"));
		when(organisationClient.resolve(organisation, recipient, RECIPIENT_TOKEN)).thenReturn(doctor(organisation, recipient, recipientMember, "Synthetic Recipient"));
		var result = new java.util.HashMap<String, Object>();
		result.put("referralRequestId", UUID.randomUUID()); result.put("recipientUserId", recipient);
		result.put("patientRegistrationId", UUID.randomUUID()); result.put("reason", "Synthetic second opinion");
		result.put("priority", "ROUTINE"); result.put("purpose", "Synthetic review"); result.put("consentType", "RECORDED_WRITTEN");
		result.put("consentEvidenceReference", "synthetic-consent"); result.put("consentRecordedAt", Instant.now().minusSeconds(60).toString());
		result.put("accessExpiresAt", Instant.now().plus(7, ChronoUnit.DAYS).toString()); result.put("sendImmediately", true);
		result.put("selectedItems", List.of(java.util.Map.of("resourceType", "DIAGNOSIS", "resourceId", UUID.randomUUID())));
		return result;
	}

	private static String decision(UUID patientRegistrationId, UUID consultationId, UUID ownerId) {
		return """
				{"patientRegistrationId":"%s","resourceType":"CONSULTATION","resourceId":"%s","resourceOwnerUserId":"%s"}
				""".formatted(patientRegistrationId, consultationId, ownerId);
	}
	private void workspace(String token, UUID organisationId, UUID userId, List<String> roles) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(token, organisationId, userId, roles));
	}
	private static CollaborationDoctorResource doctor(UUID organisationId, UUID userId,
			UUID membershipId, String name) {
		return new CollaborationDoctorResource(membershipId, organisationId, userId, name, 1);
	}
	private static Cookie access(String token) { return new Cookie("SAHHA_ACCESS_TOKEN", token); }
	private static Cookie csrf() { return new Cookie("XSRF-TOKEN", CSRF); }
	private static Jwt jwt(String token, UUID organisationId, UUID userId, List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token).header("alg", "RS256")
				.subject(userId.toString()).issuer("http://localhost:8081")
				.audience(List.of("sahha-api")).issuedAt(now).expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString()).claim("cv", 1)
				.claim("roles", List.of()).claim("org_id", organisationId.toString())
				.claim("org_roles", roles).claim("token_type", "access").build();
	}
}
