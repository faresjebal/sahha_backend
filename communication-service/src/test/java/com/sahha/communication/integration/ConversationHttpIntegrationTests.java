package com.sahha.communication.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;
import com.sahha.communication.repository.ConversationMessageRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationHttpIntegrationTests {
	private static final String DOCTOR_TOKEN = "doctor.access.token";
	private static final String OTHER_TOKEN = "other.access.token";
	private static final String RECEPTIONIST_TOKEN = "reception.access.token";
	private static final String CSRF = "communication-csrf-token";

	@Autowired MockMvc mockMvc;
	@Autowired ConversationMessageRepository messageRepository;
	@Autowired CommunicationAuditEventRepository auditRepository;
	@Autowired CommunicationOutboxEventRepository outboxRepository;
	@MockitoBean JwtDecoder jwtDecoder;
	@MockitoBean OrganisationCollaborationClient organisationClient;
	@MockitoBean SchedulingPatientContextClient patientContextClient;

	@Test
	void directConversationIsRetrySafeParticipantOnlyAndAppendOnly() throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID doctorId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		UUID recipientId = UUID.randomUUID();
		UUID recipientMembershipId = UUID.randomUUID();
		UUID otherId = UUID.randomUUID();
		UUID patientRegistrationId = UUID.randomUUID();
		UUID conversationRequestId = UUID.randomUUID();
		UUID messageRequestId = UUID.randomUUID();
		workspace(DOCTOR_TOKEN, organisationId, doctorId, List.of("DOCTOR"));
		workspace(OTHER_TOKEN, organisationId, otherId, List.of("DOCTOR"));
		workspace(RECEPTIONIST_TOKEN, organisationId, UUID.randomUUID(), List.of("RECEPTIONIST"));
		when(organisationClient.resolve(organisationId, doctorId, DOCTOR_TOKEN))
				.thenReturn(doctor(organisationId, doctorId, doctorMembershipId, "Dr Synthetic One"));
		when(organisationClient.resolve(organisationId, recipientId, DOCTOR_TOKEN))
				.thenReturn(doctor(organisationId, recipientId, recipientMembershipId, "Dr Synthetic Two"));
		when(organisationClient.resolve(organisationId, otherId, OTHER_TOKEN))
				.thenReturn(doctor(organisationId, otherId, UUID.randomUUID(), "Dr Unrelated"));

		String request = """
				{"conversationRequestId":"%s","recipientUserId":"%s","subject":"Synthetic case discussion","patientRegistrationId":"%s"}
				""".formatted(conversationRequestId, recipientId, patientRegistrationId);
		String createdBody = mockMvc.perform(post("/api/v1/conversations")
					.cookie(access(DOCTOR_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.patientRegistrationId").value(patientRegistrationId.toString()))
				.andExpect(jsonPath("$.patientAccessGranted").value(false))
				.andExpect(jsonPath("$.participants.length()").value(2))
				.andReturn().getResponse().getContentAsString();
		String conversationId = tools.jackson.databind.json.JsonMapper.builder().build()
				.readTree(createdBody).get("id").asText();

		mockMvc.perform(post("/api/v1/conversations")
					.cookie(access(DOCTOR_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON).content(request))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(conversationId));

		mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
					.cookie(access(DOCTOR_TOKEN), csrf()).header("X-XSRF-TOKEN", CSRF)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"messageRequestId":"%s","body":"Synthetic private message"}
							""".formatted(messageRequestId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("Synthetic private message"));

		mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].body").value("Synthetic private message"));
		mockMvc.perform(get("/api/v1/conversations/{id}", conversationId)
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/conversations").cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/v1/conversations/{id}/read", conversationId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		org.junit.jupiter.api.Assertions.assertEquals(1, messageRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(2, auditRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(2, outboxRepository.count());
		outboxRepository.findAll().forEach(event -> {
			org.junit.jupiter.api.Assertions.assertFalse(event.getPayload().containsKey("body"));
			org.junit.jupiter.api.Assertions.assertFalse(
					event.getPayload().containsKey("patientRegistrationId"));
		});
	}

	private void workspace(String token, UUID organisationId, UUID userId, List<String> roles) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(token, organisationId, userId, roles));
	}
	private static CollaborationDoctorResource doctor(UUID organisationId, UUID userId,
			UUID membershipId, String name) {
		return new CollaborationDoctorResource(membershipId, organisationId, userId, name, 0);
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
