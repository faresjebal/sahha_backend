package com.sahha.patient.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
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

import com.sahha.patient.client.auth.AuthAccountClient;
import com.sahha.patient.client.auth.AuthAccountResource;
import com.sahha.patient.entity.PatientIdentity;
import com.sahha.patient.entity.PatientOrganisationRegistration;
import com.sahha.patient.entity.PatientSex;
import com.sahha.patient.repository.PatientAccountLinkRepository;
import com.sahha.patient.repository.PatientAuditEventRepository;
import com.sahha.patient.repository.PatientIdentityRepository;
import com.sahha.patient.repository.PatientOrganisationRegistrationRepository;
import com.sahha.patient.repository.PatientOutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientAccountHttpIntegrationTests {

	private static final String PATIENT_TOKEN = "patient.self.token";
	private static final String OTHER_TOKEN = "patient.other.token";
	private static final String CSRF = "patient-self-csrf";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private PatientIdentityRepository identityRepository;

	@Autowired
	private PatientOrganisationRegistrationRepository registrationRepository;

	@Autowired
	private PatientAccountLinkRepository linkRepository;

	@Autowired
	private PatientAuditEventRepository auditRepository;

	@Autowired
	private PatientOutboxEventRepository outboxRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private AuthAccountClient authAccountClient;

	@Test
	void verifiedAccountLinksOnceAndReadsOnlyItsOwnMinimumRegistration()
			throws Exception {
		UUID patientUserId = UUID.randomUUID();
		UUID otherUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		LocalDate dateOfBirth = LocalDate.of(1994, 4, 17);
		PatientOrganisationRegistration registration = registration(
				organisationId, dateOfBirth, "patient@example.test");
		when(jwtDecoder.decode(PATIENT_TOKEN)).thenReturn(jwt(
				PATIENT_TOKEN, patientUserId));
		when(jwtDecoder.decode(OTHER_TOKEN)).thenReturn(jwt(
				OTHER_TOKEN, otherUserId));
		when(authAccountClient.currentAccount(PATIENT_TOKEN)).thenReturn(
				new AuthAccountResource(
						patientUserId,
						"patient@example.test",
						"Synthetic",
						"Patient",
						"ACTIVE",
						true));

		String body = """
				{
				  "organisationId":"%s",
				  "medicalRecordNumber":"%s",
				  "dateOfBirth":"%s"
				}
				""".formatted(
				organisationId,
				registration.getMedicalRecordNumber(),
				dateOfBirth);
		mockMvc.perform(post("/api/v1/patients/me/account-link")
					.cookie(access(PATIENT_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF)
					.header("X-Request-ID", "patient-link-test")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.authUserId")
						.value(patientUserId.toString()));

		mockMvc.perform(get("/api/v1/patients/me/registrations")
					.cookie(access(PATIENT_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].registrationId")
						.value(registration.getId().toString()))
				.andExpect(jsonPath("$[0].organisationId")
						.value(organisationId.toString()))
				.andExpect(jsonPath("$[0].email").doesNotExist())
				.andExpect(jsonPath("$[0].patientId").doesNotExist());

		mockMvc.perform(get(
					"/api/v1/patients/me/registrations/{registrationId}/scheduling-context",
					registration.getId())
					.cookie(access(PATIENT_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.authUserId")
						.value(patientUserId.toString()));

		mockMvc.perform(get(
					"/api/v1/patients/me/registrations/{registrationId}/scheduling-context",
					registration.getId())
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isNotFound());

		org.junit.jupiter.api.Assertions.assertEquals(1, linkRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(1, auditRepository.count());
		org.junit.jupiter.api.Assertions.assertEquals(1, outboxRepository.count());
		org.junit.jupiter.api.Assertions.assertFalse(
				outboxRepository.findAll().getFirst().getPayload()
						.containsKey("medicalRecordNumber"));
	}

	private PatientOrganisationRegistration registration(
			UUID organisationId,
			LocalDate dateOfBirth,
			String email) {
		UUID registrar = UUID.randomUUID();
		Instant now = Instant.now();
		PatientIdentity identity = identityRepository.saveAndFlush(
				PatientIdentity.create(
						"Synthetic", "synthetic", "Patient", "patient",
						dateOfBirth, PatientSex.UNDISCLOSED,
						null, null, null, null, registrar, now));
		return registrationRepository.saveAndFlush(
				PatientOrganisationRegistration.create(
						identity,
						organisationId,
						"+21620000000",
						"+21620000000",
						email,
						email,
						"Synthetic address",
						"Tunis",
						"Tunis",
						"1000",
						"TN",
						null, null, null, null, null,
						registrar,
						now));
	}

	private static Jwt jwt(String token, UUID userId) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token)
				.header("alg", "RS256")
				.subject(userId.toString())
				.issuer("http://localhost:8081")
				.audience(List.of("sahha-api"))
				.issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString())
				.claim("cv", 1)
				.claim("roles", List.of())
				.claim("org_roles", List.of())
				.claim("token_type", "access")
				.build();
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", CSRF);
	}
}
