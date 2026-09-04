package com.sahha.scheduling.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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

import com.sahha.scheduling.client.organisation.OrganisationContextClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.client.patient.PatientRegistryClient;
import com.sahha.scheduling.client.patient.PatientSchedulingContextResource;
import com.sahha.scheduling.entity.AppointmentActorType;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PatientAppointmentHttpIntegrationTests {

	private static final String PATIENT_TOKEN = "scheduling.patient.token";
	private static final String CSRF = "scheduling-patient-csrf";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DoctorAvailabilityScheduleRepository availabilityRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentAuditEventRepository auditRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@MockitoBean
	private PatientRegistryClient patientRegistryClient;

	@MockitoBean
	private SchedulingDoctorDirectoryClient doctorDirectoryClient;

	@Test
	void linkedPatientListsDoctorsRequestsSlotAndReadsMinimumOwnStatus()
			throws Exception {
		UUID patientUserId = UUID.randomUUID();
		UUID organisationId = UUID.randomUUID();
		UUID registrationId = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		UUID bookingRequestId = UUID.randomUUID();
		LocalDate monday = LocalDate.now(ZoneOffset.UTC).plusDays(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
		Instant startsAt = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);
		when(jwtDecoder.decode(PATIENT_TOKEN)).thenReturn(jwt(
				PATIENT_TOKEN, patientUserId));
		PatientSchedulingContextResource context =
				new PatientSchedulingContextResource(
						registrationId,
						patientId,
						organisationId,
						"ACTIVE",
						patientUserId);
		when(patientRegistryClient.findOwnActiveRegistration(
				registrationId, patientUserId, PATIENT_TOKEN)).thenReturn(context);
		SchedulingDoctorResource doctor = new SchedulingDoctorResource(
				doctorMembershipId,
				organisationId,
				doctorUserId,
				"Dr Synthetic Patient",
				0);
		when(doctorDirectoryClient.findPatientVisibleActiveDoctor(
				organisationId, doctorUserId, PATIENT_TOKEN)).thenReturn(doctor);
		availabilityRepository.saveAndFlush(DoctorAvailabilitySchedule.create(
				organisationId,
				doctorUserId,
				doctorMembershipId,
				"UTC",
				30,
				0,
				60,
				"Synthetic patient room",
				List.of(new DoctorAvailabilitySchedule.WeeklyWindowValue(
						DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(10, 0))),
				List.of(),
				List.of(),
				doctorUserId,
				clock));

		mockMvc.perform(get(
					"/api/v1/availability/mine/registrations/{registrationId}/doctors",
					registrationId)
					.cookie(access(PATIENT_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].displayName")
						.value("Dr Synthetic Patient"))
				.andExpect(jsonPath("$[0].doctorMembershipId").doesNotExist());

		mockMvc.perform(get(
					"/api/v1/availability/mine/registrations/{registrationId}/doctors/{doctorUserId}/slots",
					registrationId,
					doctorUserId)
					.cookie(access(PATIENT_TOKEN))
					.param("from", monday.toString())
					.param("to", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots.length()").value(2));

		mockMvc.perform(post("/api/v1/appointments/mine")
					.cookie(access(PATIENT_TOKEN), csrf())
					.header("X-XSRF-TOKEN", CSRF)
					.header("X-Request-ID", "patient-appointment-test")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "bookingRequestId":"%s",
							  "patientRegistrationId":"%s",
							  "doctorUserId":"%s",
							  "startsAt":"%s"
							}
							""".formatted(
							bookingRequestId, registrationId, doctorUserId, startsAt)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.patientId").doesNotExist())
				.andExpect(jsonPath("$.patientRegistrationId").doesNotExist())
				.andExpect(jsonPath("$.bookingRequestId").doesNotExist())
				.andExpect(jsonPath("$.bookedByUserId").doesNotExist());

		mockMvc.perform(get("/api/v1/appointments/mine")
					.cookie(access(PATIENT_TOKEN))
					.param("registrationId", registrationId.toString())
					.param("from", startsAt.minusSeconds(3600).toString())
					.param("to", startsAt.plusSeconds(3600).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].doctorUserId")
						.value(doctorUserId.toString()));

		var appointment = appointmentRepository
				.findByOrganisationIdAndBookingRequestId(
						organisationId, bookingRequestId)
				.orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(
				AppointmentActorType.PATIENT,
				appointment.getBookedByActorType());
		org.junit.jupiter.api.Assertions.assertNull(
				appointment.getBookedByMembershipId());
		var audit = auditRepository.findByOrganisationIdAndCommandRequestId(
				organisationId, bookingRequestId).orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(
				AppointmentActorType.PATIENT,
				audit.getActorType());
		org.junit.jupiter.api.Assertions.assertNull(audit.getActorMembershipId());
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
