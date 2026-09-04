package com.sahha.clinical.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.sahha.clinical.client.scheduling.ClinicalAppointmentContextResource;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.exception.SchedulingContextUnavailableException;
import com.sahha.clinical.repository.ClinicalAuditEventRepository;
import com.sahha.clinical.repository.ClinicalOutboxEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConsultationHttpIntegrationTests {

	private static final String DOCTOR_TOKEN = "aaa.bbb.ccc";
	private static final String OTHER_DOCTOR_TOKEN = "ddd.eee.fff";
	private static final String RECEPTIONIST_TOKEN = "ggg.hhh.iii";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ClinicalAuditEventRepository auditRepository;

	@Autowired
	private ClinicalOutboxEventRepository outboxRepository;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private SchedulingClinicalContextClient schedulingClient;

	@Test
	void owningDoctorCreatesRetrySafeDraftThenUpdatesWithVersionProtection()
			throws Exception {
		Fixture fixture = fixture("IN_PROGRESS");
		doctorWorkspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId());
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());

		String firstBody = mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.header("X-Request-ID", "clinical-create-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.organisationId")
						.value(fixture.organisationId().toString()))
				.andExpect(jsonPath("$.patientId")
						.value(fixture.patientId().toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(fixture.doctorUserId().toString()))
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.version").value(0))
				.andReturn().getResponse().getContentAsString();
		String consultationId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(firstBody).get("id").asText();

		mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(consultationId))
				.andExpect(jsonPath("$.version").value(0));

		mockMvc.perform(patch(
				"/api/v1/consultations/{consultationId}/draft", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.header("X-Request-ID", "clinical-update-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateRequest(0, "Persistent cough", "Synthetic draft note")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.reasonForConsultation")
						.value("Persistent cough"))
				.andExpect(jsonPath("$.draftNotes").value("Synthetic draft note"))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(patch(
				"/api/v1/consultations/{consultationId}/draft", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateRequest(0, "Stale reason", "Stale note")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-consultation-modification"));

		UUID id = UUID.fromString(consultationId);
		org.junit.jupiter.api.Assertions.assertEquals(
				2, auditRepository.countByConsultationId(id));
		org.junit.jupiter.api.Assertions.assertEquals(
				2, outboxRepository.countByAggregateId(id));
	}

	@Test
	void onlyOwningDoctorCanReadAndReceptionistCannotEnterClinicalApi()
			throws Exception {
		Fixture fixture = fixture("IN_PROGRESS");
		UUID otherDoctorId = UUID.randomUUID();
		doctorWorkspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId());
		doctorWorkspace(OTHER_DOCTOR_TOKEN, fixture.organisationId(), otherDoctorId);
		workspace(RECEPTIONIST_TOKEN, fixture.organisationId(), UUID.randomUUID(),
				List.of("RECEPTIONIST"));
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());

		String body = mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String consultationId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(body).get("id").asText();

		mockMvc.perform(get("/api/v1/consultations/{id}", consultationId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/consultations/{id}", consultationId)
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/v1/consultations/{id}", consultationId)
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsWrongOrganisationWrongStatusMissingCsrfAndUnavailableScheduling()
			throws Exception {
		Fixture fixture = fixture("CONFIRMED");
		doctorWorkspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId());
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());

		mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isConflict());

		mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isForbidden());

		Fixture wrongOrganisation = fixture("IN_PROGRESS");
		when(schedulingClient.resolve(wrongOrganisation.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(wrongOrganisation.context());
		mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(wrongOrganisation.appointmentId())))
				.andExpect(status().isForbidden());

		UUID unavailableAppointment = UUID.randomUUID();
		when(schedulingClient.resolve(unavailableAppointment, DOCTOR_TOKEN))
				.thenThrow(new SchedulingContextUnavailableException());
		mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(unavailableAppointment)))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void attachmentContextIsMinimalAndHiddenOutsideTheOwningDoctor()
			throws Exception {
		Fixture fixture = fixture("IN_PROGRESS");
		UUID otherDoctorId = UUID.randomUUID();
		doctorWorkspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId());
		doctorWorkspace(OTHER_DOCTOR_TOKEN, fixture.organisationId(), otherDoctorId);
		workspace(RECEPTIONIST_TOKEN, fixture.organisationId(), UUID.randomUUID(),
				List.of("RECEPTIONIST"));
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());

		String body = mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "clinical-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createRequest(fixture.appointmentId())))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String consultationId = tools.jackson.databind.json.JsonMapper.builder()
				.build().readTree(body).get("id").asText();
		String path = "/api/v1/internal/clinical/consultations/{id}/attachment-context";

		mockMvc.perform(get(path, consultationId).cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.consultationId").value(consultationId))
				.andExpect(jsonPath("$.organisationId")
						.value(fixture.organisationId().toString()))
				.andExpect(jsonPath("$.patientRegistrationId")
						.value(fixture.patientRegistrationId().toString()))
				.andExpect(jsonPath("$.patientId")
						.value(fixture.patientId().toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(fixture.doctorUserId().toString()))
				.andExpect(jsonPath("$.consultationStatus").value("DRAFT"))
				.andExpect(jsonPath("$.reasonForConsultation").doesNotExist())
				.andExpect(jsonPath("$.draftNotes").doesNotExist());
		mockMvc.perform(get(path, consultationId)
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(path, consultationId)
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	private void doctorWorkspace(String token, UUID organisationId, UUID userId) {
		workspace(token, organisationId, userId, List.of("DOCTOR"));
	}

	private void workspace(
			String token,
			UUID organisationId,
			UUID userId,
			List<String> roles) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(token, userId, organisationId, roles));
	}

	private static Jwt jwt(
			String token,
			UUID userId,
			UUID organisationId,
			List<String> roles) {
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
				.claim("org_id", organisationId.toString())
				.claim("org_roles", roles)
				.claim("token_type", "access")
				.build();
	}

	private static Fixture fixture(String status) {
		UUID organisationId = UUID.randomUUID();
		UUID appointmentId = UUID.randomUUID();
		UUID patientRegistrationId = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		return new Fixture(
				organisationId, appointmentId, patientRegistrationId, patientId,
				doctorUserId, doctorMembershipId, status);
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", "clinical-csrf-token");
	}

	private static String createRequest(UUID appointmentId) {
		return "{\"appointmentId\":\"%s\"}".formatted(appointmentId);
	}

	private static String updateRequest(long version, String reason, String notes) {
		return """
				{
				  "version":%d,
				  "reasonForConsultation":"%s",
				  "draftNotes":"%s"
				}
				""".formatted(version, reason, notes);
	}

	private record Fixture(
			UUID organisationId,
			UUID appointmentId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			String status) {

		ClinicalAppointmentContextResource context() {
			Instant startsAt = Instant.now().minusSeconds(600);
			return new ClinicalAppointmentContextResource(
					appointmentId, organisationId, patientRegistrationId, patientId,
					doctorUserId, doctorMembershipId, status,
					startsAt, startsAt.plusSeconds(1800), 3);
		}
	}
}
