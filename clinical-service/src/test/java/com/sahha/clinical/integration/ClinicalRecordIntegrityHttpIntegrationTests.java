package com.sahha.clinical.integration;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import com.sahha.clinical.client.organisation.OrganisationDoctorClient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
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
import tools.jackson.databind.ObjectMapper;

import com.sahha.clinical.client.scheduling.ClinicalAppointmentContextResource;
import com.sahha.clinical.client.scheduling.ClinicalPatientAccessContextResource;
import com.sahha.clinical.client.scheduling.ClinicalAppointmentCompletionResource;
import com.sahha.clinical.client.scheduling.SchedulingClinicalContextClient;
import com.sahha.clinical.exception.ClinicalAppointmentNotFoundException;
import com.sahha.clinical.repository.ClinicalAuditEventRepository;
import com.sahha.clinical.repository.ClinicalAccessAuditEventRepository;
import com.sahha.clinical.repository.ClinicalCorrectionRepository;
import com.sahha.clinical.repository.ClinicalOutboxEventRepository;
import com.sahha.clinical.repository.ConsultationRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClinicalRecordIntegrityHttpIntegrationTests {

	private static final String DOCTOR_TOKEN = "phase.fiveb.doctor";
	private static final String OTHER_TOKEN = "phase.fiveb.other";
	private static final String UNRELATED_TOKEN = "phase.fivec.unrelated";
	private static final String RECEPTIONIST_TOKEN = "phase.fivec.receptionist";

	@Autowired private MockMvc mockMvc;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private ConsultationRepository consultationRepository;
	@Autowired private ClinicalAuditEventRepository auditRepository;
	@Autowired private ClinicalAccessAuditEventRepository accessAuditRepository;
	@Autowired private ClinicalOutboxEventRepository outboxRepository;
	@Autowired private ClinicalCorrectionRepository correctionRepository;
	@Autowired private JdbcTemplate jdbcTemplate;
	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private SchedulingClinicalContextClient schedulingClient;
	@MockitoBean private OrganisationDoctorClient doctors;

	@Test
	void structuredDraftFinalizationAndCorrectionsPreserveSignedSource()
			throws Exception {
		Fixture fixture = fixture();
		workspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId(),
				List.of("DOCTOR"));
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());
		UUID consultationId = create(fixture.appointmentId());

		String structuredBody = mockMvc.perform(put(
				"/api/v1/consultations/{id}/draft-content", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.header("X-Request-ID", "structured-draft-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(completeDraft(0)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andExpect(jsonPath("$.symptoms.length()").value(1))
				.andExpect(jsonPath("$.history.length()").value(2))
				.andExpect(jsonPath("$.vitalSigns.temperatureCelsius").value(37.2))
				.andExpect(jsonPath("$.examinationFindings.length()").value(1))
				.andExpect(jsonPath("$.diagnoses[0].label").value("Acute bronchitis"))
				.andExpect(jsonPath("$.medications[0].kind").value("PRESCRIBED"))
				.andExpect(jsonPath("$.version").value(1))
				.andReturn().getResponse().getContentAsString();
		String symptomId = objectMapper.readTree(structuredBody)
				.get("symptoms").get(0).get("id").asText();

		mockMvc.perform(post("/api/v1/consultations/{id}/finalize", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.header("X-Request-ID", "finalize-record-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":1}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FINALIZED"))
				.andExpect(jsonPath("$.finalizedAt").isNotEmpty())
				.andExpect(jsonPath("$.finalizedByUserId")
						.value(fixture.doctorUserId().toString()))
				.andExpect(jsonPath("$.version").value(2));

		mockMvc.perform(put(
				"/api/v1/consultations/{id}/draft-content", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content(completeDraft(2)))
				.andExpect(status().isConflict());

		mockMvc.perform(post(
				"/api/v1/consultations/{id}/corrections", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.header("X-Request-ID", "assessment-correction-request")
					.contentType(MediaType.APPLICATION_JSON)
					.content(correction(
							2, "CONSULTATION", null, "clinicalAssessment",
							"Corrected assessment", "Dictation correction")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clinicalAssessment")
						.value("Corrected assessment"))
				.andExpect(jsonPath("$.corrections[0].oldValue")
						.value("Likely uncomplicated acute bronchitis."))
				.andExpect(jsonPath("$.corrections[0].reason")
						.value("Dictation correction"))
				.andExpect(jsonPath("$.version").value(3));

		mockMvc.perform(post(
				"/api/v1/consultations/{id}/corrections", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content(correction(
							3, "SYMPTOM", symptomId, "name",
							"Productive cough", "Patient clarified symptom")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.symptoms[0].name").value("Productive cough"))
				.andExpect(jsonPath("$.corrections.length()").value(2))
				.andExpect(jsonPath("$.version").value(4));

		mockMvc.perform(post(
				"/api/v1/consultations/{id}/corrections", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content(correction(
							3, "CONSULTATION", null, "treatmentPlan",
							"Stale change", "Stale version")))
				.andExpect(status().isConflict());

		var source = consultationRepository.findById(consultationId).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(
				"Likely uncomplicated acute bronchitis.",
				source.getClinicalAssessment());
		org.junit.jupiter.api.Assertions.assertEquals(
				2, correctionRepository.countByConsultationId(consultationId));
		org.junit.jupiter.api.Assertions.assertEquals(
				5, auditRepository.countByConsultationId(consultationId));
		org.junit.jupiter.api.Assertions.assertEquals(
				5, outboxRepository.countByAggregateId(consultationId));
		outboxRepository.findAll().stream()
				.filter(event -> event.getAggregateId().equals(consultationId))
				.forEach(event -> {
					org.junit.jupiter.api.Assertions.assertEquals(
							Set.of(
									"eventId", "eventType", "schemaVersion", "occurredAt", "actorUserId",
									"consultationId", "appointmentId", "organisationId",
									"status", "resourceVersion"),
							event.getPayload().keySet());
					String payload = event.getPayload().toString();
					org.junit.jupiter.api.Assertions.assertFalse(
							payload.contains("bronchitis")
									|| payload.contains("medicine")
									|| payload.contains("assessment"));
				});
		org.junit.jupiter.api.Assertions.assertTrue(
				outboxRepository.findAll().stream().anyMatch(event ->
						"consultation.finalised.v1".equals(event.getEventType())
								&& "consultation.finalised.v1".equals(
										event.getPayload().get("eventType"))));
		org.junit.jupiter.api.Assertions.assertTrue(
				outboxRepository.findAll().stream().anyMatch(event ->
						"consultation.corrected.v1".equals(event.getEventType())
								&& "consultation.corrected.v1".equals(
										event.getPayload().get("eventType"))));

		mockMvc.perform(get("/api/v1/consultations/{id}/record", consultationId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clinicalAssessment")
						.value("Corrected assessment"));
		org.junit.jupiter.api.Assertions.assertEquals(
				8, accessAuditRepository.countByResourceTypeAndResourceId(
						"CONSULTATION", consultationId));
		// Seven live-author decisions plus the effective-record read audit.
		org.junit.jupiter.api.Assertions.assertEquals(7,
				accessAuditRepository.findAll().stream().filter(event ->
						consultationId.equals(event.getResourceId())
						&& "AUTHOR_MEMBERSHIP_VERIFIED".equals(event.getAccessReason())).count());

		UUID finalizationEventId = outboxRepository
				.findFirstByAggregateIdAndEventTypeOrderByOccurredAtDesc(
						consultationId, "consultation.finalised.v1")
				.orElseThrow().getId();
		when(schedulingClient.recoverCompletion(
				eq(fixture.appointmentId()), any(), eq(DOCTOR_TOKEN),
				eq("phase-fiveb-csrf")))
				.thenReturn(new ClinicalAppointmentCompletionResource(
						finalizationEventId,
						fixture.appointmentId(),
						"APPLIED",
						false,
						"COMPLETED",
						4L,
						null));
		mockMvc.perform(post(
				"/api/v1/consultations/{id}/appointment-completion-recovery",
				consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":4}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.outcome").value("APPLIED"))
				.andExpect(jsonPath("$.appointmentStatus").value("COMPLETED"));

		org.junit.jupiter.api.Assertions.assertThrows(
				org.springframework.dao.DataAccessException.class,
				() -> jdbcTemplate.update(
						"update clinical_consultation set clinical_assessment = ? where id = ?",
						"Silent overwrite", consultationId));
	}

	@Test
	void incompleteDraftAndUnrelatedDoctorAreDeniedWithoutFinalization()
			throws Exception {
		Fixture fixture = fixture();
		UUID otherDoctor = UUID.randomUUID();
		workspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId(),
				List.of("DOCTOR"));
		workspace(OTHER_TOKEN, fixture.organisationId(), otherDoctor,
				List.of("DOCTOR"));
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());
		UUID consultationId = create(fixture.appointmentId());

		mockMvc.perform(post("/api/v1/consultations/{id}/finalize", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":0}"))
				.andExpect(status().isUnprocessableEntity())
				.andExpect(jsonPath("$.type")
						.value("urn:sahha:problem:consultation-incomplete"));

		mockMvc.perform(get("/api/v1/consultations/{id}/record", consultationId)
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isNotFound());
		org.junit.jupiter.api.Assertions.assertEquals(
				2, accessAuditRepository.countByResourceTypeAndResourceId(
						"CONSULTATION", consultationId));
		mockMvc.perform(put(
				"/api/v1/consultations/{id}/draft-content", consultationId)
					.cookie(access(OTHER_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content(completeDraft(0)))
				.andExpect(status().isNotFound());
	}

	@Test
	void activeTreatingDoctorReceivesOnlyTheBoundedFinalizedSummary()
			throws Exception {
		Fixture fixture = fixture();
		UUID treatingDoctor = UUID.randomUUID();
		UUID treatingMembership = UUID.randomUUID();
		workspace(DOCTOR_TOKEN, fixture.organisationId(), fixture.doctorUserId(),
				List.of("DOCTOR"));
		workspace(OTHER_TOKEN, fixture.organisationId(), treatingDoctor,
				List.of("DOCTOR"));
		when(schedulingClient.resolve(fixture.appointmentId(), DOCTOR_TOKEN))
				.thenReturn(fixture.context());
		UUID consultationId = create(fixture.appointmentId());
		mockMvc.perform(put(
				"/api/v1/consultations/{id}/draft-content", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content(completeDraft(0)))
				.andExpect(status().isOk());
		mockMvc.perform(post(
				"/api/v1/consultations/{id}/finalize", consultationId)
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"version\":1}"))
				.andExpect(status().isOk());

		UUID careAppointmentId = UUID.randomUUID();
		when(schedulingClient.resolvePatientAccess(
				fixture.patientRegistrationId(), OTHER_TOKEN))
				.thenReturn(new ClinicalPatientAccessContextResource(
						careAppointmentId,
						fixture.organisationId(),
						fixture.patientRegistrationId(),
						fixture.patientId(),
						treatingDoctor,
						treatingMembership,
						"CONFIRMED",
						2));

		mockMvc.perform(get(
				"/api/v1/clinical/patients/{patientRegistrationId}/summary",
				fixture.patientRegistrationId())
					.cookie(access(OTHER_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.patientId")
						.value(fixture.patientId().toString()))
				.andExpect(jsonPath("$.careRelationship.appointmentId")
						.value(careAppointmentId.toString()))
				.andExpect(jsonPath("$.encounters.length()").value(1))
				.andExpect(jsonPath("$.encounters[0].consultationId")
						.value(consultationId.toString()))
				.andExpect(jsonPath("$.encounters[0].diagnoses[0].label")
						.value("Acute bronchitis"))
				.andExpect(jsonPath("$.encounters[0].allergies[0]")
						.value("No known drug allergy"))
				.andExpect(jsonPath("$.encounters[0].clinicalAssessment")
						.doesNotExist())
				.andExpect(jsonPath("$.encounters[0].additionalNotes")
						.doesNotExist())
				.andExpect(jsonPath("$.encounters[0].corrections")
						.doesNotExist());
		org.junit.jupiter.api.Assertions.assertEquals(
				1, accessAuditRepository.countByResourceTypeAndResourceId(
						"PATIENT_SUMMARY", fixture.patientRegistrationId()));

		workspace(UNRELATED_TOKEN, fixture.organisationId(), UUID.randomUUID(),
				List.of("DOCTOR"));
		when(schedulingClient.resolvePatientAccess(
				fixture.patientRegistrationId(), UNRELATED_TOKEN))
				.thenThrow(new ClinicalAppointmentNotFoundException());
		mockMvc.perform(get(
				"/api/v1/clinical/patients/{patientRegistrationId}/summary",
				fixture.patientRegistrationId())
					.cookie(access(UNRELATED_TOKEN)))
				.andExpect(status().isNotFound());
		org.junit.jupiter.api.Assertions.assertEquals(
				2, accessAuditRepository.countByResourceTypeAndResourceId(
						"PATIENT_SUMMARY", fixture.patientRegistrationId()));

		workspace(RECEPTIONIST_TOKEN, fixture.organisationId(), UUID.randomUUID(),
				List.of("RECEPTIONIST"));
		mockMvc.perform(get(
				"/api/v1/clinical/patients/{patientRegistrationId}/summary",
				fixture.patientRegistrationId())
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	private UUID create(UUID appointmentId) throws Exception {
		String body = mockMvc.perform(post("/api/v1/consultations")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "phase-fiveb-csrf")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"appointmentId\":\"%s\"}".formatted(appointmentId)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(objectMapper.readTree(body).get("id").asText());
	}

	private void workspace(
			String token, UUID organisationId, UUID userId, List<String> roles) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(token, userId, organisationId, roles));
	}

	private static Jwt jwt(
			String token, UUID userId, UUID organisationId, List<String> roles) {
		Instant now = Instant.now();
		return Jwt.withTokenValue(token).header("alg", "RS256")
				.subject(userId.toString()).issuer("http://localhost:8081")
				.audience(List.of("sahha-api")).issuedAt(now)
				.expiresAt(now.plusSeconds(300))
				.claim("sid", UUID.randomUUID().toString()).claim("cv", 1)
				.claim("roles", List.of()).claim("org_id", organisationId.toString())
				.claim("org_roles", roles).claim("token_type", "access").build();
	}

	private Fixture fixture() {
		Fixture value = new Fixture(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(doctors.requireCurrentMembership(eq(value.organisationId()), eq(value.doctorUserId()),
				eq(DOCTOR_TOKEN), anyString())).thenReturn(value.doctorMembershipId());
		return value;
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", "phase-fiveb-csrf");
	}

	private static String completeDraft(long version) {
		return """
				{
				  "version":%d,
				  "reasonForConsultation":"Persistent cough and fever",
				  "clinicalAssessment":"Likely uncomplicated acute bronchitis.",
				  "treatmentPlan":"Supportive treatment and prescribed medication.",
				  "followUpInstructions":"Return in seven days or sooner if breathing worsens.",
				  "additionalNotes":"Synthetic internship consultation only.",
				  "symptoms":[{"name":"Cough","onsetDescription":"Five days","severity":"MODERATE","notes":null}],
				  "history":[
				    {"category":"MEDICAL","description":"No chronic respiratory illness","notes":null},
				    {"category":"ALLERGY","description":"No known drug allergy","notes":null}
				  ],
				  "vitalSigns":{
				    "measuredAt":"2026-08-24T20:00:00Z",
				    "temperatureCelsius":37.2,
				    "systolicBloodPressure":118,
				    "diastolicBloodPressure":76,
				    "heartRateBpm":82,
				    "respiratoryRateBpm":16,
				    "oxygenSaturationPercent":98.0,
				    "weightKg":72.5,
				    "heightCm":175.0,
				    "specialtyMeasurements":{"peakFlowLMin":420}
				  },
				  "examinationFindings":[{"bodySystem":"Respiratory","finding":"Scattered rhonchi","notes":"No distress"}],
				  "diagnoses":[{"code":"J20.9","codeSystem":"ICD-10","label":"Acute bronchitis","type":"PRIMARY","status":"CONFIRMED","notes":null}],
				  "medications":[{"kind":"PRESCRIBED","name":"Synthetic medicine","strength":"500 mg","form":"Tablet","dosage":"One tablet","frequency":"Twice daily","route":"Oral","duration":"Five days","quantity":"10","specialInstructions":"Take after food"}]
				}
				""".formatted(version);
	}

	private static String correction(
			long version, String targetType, String targetId, String fieldName,
			String newValue, String reason) {
		String target = targetId == null ? "null" : "\"" + targetId + "\"";
		return """
				{
				  "version":%d,
				  "targetType":"%s",
				  "targetId":%s,
				  "fieldName":"%s",
				  "newValue":"%s",
				  "reason":"%s"
				}
				""".formatted(version, targetType, target, fieldName, newValue, reason);
	}

	private record Fixture(
			UUID organisationId, UUID appointmentId, UUID patientRegistrationId,
			UUID patientId, UUID doctorUserId, UUID doctorMembershipId) {
		ClinicalAppointmentContextResource context() {
			Instant startsAt = Instant.now().minusSeconds(600);
			return new ClinicalAppointmentContextResource(
					appointmentId, organisationId, patientRegistrationId, patientId,
					doctorUserId, doctorMembershipId, "IN_PROGRESS", startsAt,
					startsAt.plusSeconds(1800), 4);
		}
	}
}
