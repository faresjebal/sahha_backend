package com.sahha.scheduling.integration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.scheduling.client.organisation.OrganisationContextClient;
import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.client.patient.PatientRegistrationResource;
import com.sahha.scheduling.client.patient.PatientRegistryClient;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.AppointmentOutboxEventRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentBookingHttpIntegrationTests {

	private static final String RECEPTIONIST_TOKEN = "booking.reception.token";
	private static final String DOCTOR_TOKEN = "booking.doctor.token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DoctorAvailabilityScheduleRepository availabilityRepository;

	@Autowired
	private AppointmentRepository appointmentRepository;

	@Autowired
	private AppointmentAuditEventRepository auditRepository;

	@Autowired
	private AppointmentOutboxEventRepository outboxRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@MockitoBean
	private SchedulingDoctorDirectoryClient doctorDirectoryClient;

	@MockitoBean
	private PatientRegistryClient patientRegistryClient;

	@Test
	void receptionistBooksOnceIdempotentlyAndOccupiedSlotDisappears()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		UUID actorMembershipId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		UUID patientRegistrationId = UUID.randomUUID();
		UUID patientId = UUID.randomUUID();
		LocalDate monday = nextMonday();
		Instant startsAt = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);
		UUID bookingRequestId = UUID.randomUUID();

		workspace(
				RECEPTIONIST_TOKEN,
				organisationId,
				actorUserId,
				actorMembershipId,
				"RECEPTIONIST");
		when(doctorDirectoryClient.findActiveDoctor(
				organisationId, doctorUserId, RECEPTIONIST_TOKEN))
				.thenReturn(new SchedulingDoctorResource(
						doctorMembershipId,
						organisationId,
						doctorUserId,
						"Dr Synthetic Booking",
						0));
		when(patientRegistryClient.findActiveRegistration(
				organisationId, patientRegistrationId, RECEPTIONIST_TOKEN))
				.thenReturn(new PatientRegistrationResource(
						patientRegistrationId,
						patientId,
						organisationId,
						"ACTIVE"));
		availabilityRepository.saveAndFlush(schedule(
				organisationId,
				doctorUserId,
				doctorMembershipId,
				monday));

		String request = bookingBody(
				bookingRequestId,
				patientRegistrationId,
				doctorUserId,
				startsAt);
		String location = mockMvc.perform(post("/api/v1/appointments")
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "booking-csrf-token")
					.header("X-Request-ID", "appointment-booking-http")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isCreated())
				.andExpect(header().exists(HttpHeaders.LOCATION))
				.andExpect(jsonPath("$.organisationId")
						.value(organisationId.toString()))
				.andExpect(jsonPath("$.patientRegistrationId")
						.value(patientRegistrationId.toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(doctorUserId.toString()))
				.andExpect(jsonPath("$.status").value("REQUESTED"))
				.andExpect(jsonPath("$.startsAt").value(startsAt.toString()))
				.andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

		mockMvc.perform(post("/api/v1/appointments")
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "booking-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isOk())
				.andExpect(header().doesNotExist(HttpHeaders.LOCATION));

		mockMvc.perform(post("/api/v1/appointments")
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "booking-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(bookingBody(
							UUID.randomUUID(),
							patientRegistrationId,
							doctorUserId,
							startsAt)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:appointment-slot-unavailable"));

		mockMvc.perform(get(
					"/api/v1/availability/doctors/{doctorUserId}/slots",
					doctorUserId)
					.cookie(access(RECEPTIONIST_TOKEN))
					.param("from", monday.toString())
					.param("to", monday.toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.slots.length()").value(1))
				.andExpect(jsonPath("$.slots[0].localStartTime")
						.value("09:30:00"));

		org.junit.jupiter.api.Assertions.assertNotNull(location);
		org.junit.jupiter.api.Assertions.assertEquals(
				1, appointmentRepository.countByOrganisationId(organisationId));
		UUID appointmentId = appointmentRepository
				.findByOrganisationIdAndBookingRequestId(
						organisationId, bookingRequestId)
				.orElseThrow()
				.getId();
		org.junit.jupiter.api.Assertions.assertEquals(
				1, auditRepository.countByAppointmentId(appointmentId));
		org.junit.jupiter.api.Assertions.assertEquals(
				1, outboxRepository.countByAppointmentId(appointmentId));
		var outbox = outboxRepository.findAll().stream()
				.filter(event -> event.getAppointmentId().equals(appointmentId))
				.findFirst()
				.orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(
				"APPOINTMENT_REQUESTED", outbox.getEventType().name());
		org.junit.jupiter.api.Assertions.assertEquals(
				appointmentId.toString(), outbox.getPayload().get("appointmentId"));
		org.junit.jupiter.api.Assertions.assertFalse(
				outbox.getPayload().containsKey("patientRegistrationId"));
		org.junit.jupiter.api.Assertions.assertFalse(
				outbox.getPayload().containsKey("actorMembershipId"));

        workspace(DOCTOR_TOKEN, organisationId, doctorUserId, doctorMembershipId, "DOCTOR");
        when(doctorDirectoryClient.findActiveDoctor(organisationId, doctorUserId, DOCTOR_TOKEN))
                .thenReturn(new SchedulingDoctorResource(doctorMembershipId, organisationId,
                        doctorUserId, "Synthetic retry doctor", 1));
        Instant moved = startsAt.plusSeconds(1800);
        mockMvc.perform(post("/api/v1/appointments/{id}/reschedule", appointmentId)
                .cookie(access(DOCTOR_TOKEN), csrf()).header("X-XSRF-TOKEN", "booking-csrf-token")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"commandRequestId":"%s","version":0,"startsAt":"%s","reason":"Synthetic retry regression"}
                    """.formatted(UUID.randomUUID(), moved)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESCHEDULED"));
        mockMvc.perform(post("/api/v1/appointments").cookie(access(RECEPTIONIST_TOKEN), csrf())
                .header("X-XSRF-TOKEN", "booking-csrf-token").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(appointmentId.toString()))
                .andExpect(jsonPath("$.startsAt").value(moved.toString()));
        mockMvc.perform(post("/api/v1/appointments").cookie(access(RECEPTIONIST_TOKEN), csrf())
                .header("X-XSRF-TOKEN", "booking-csrf-token").contentType(MediaType.APPLICATION_JSON)
                .content(bookingBody(bookingRequestId, patientRegistrationId, doctorUserId, moved)))
                .andExpect(status().isConflict());
        org.junit.jupiter.api.Assertions.assertEquals(1, appointmentRepository.countByOrganisationId(organisationId));
        org.junit.jupiter.api.Assertions.assertEquals(2, auditRepository.countByAppointmentId(appointmentId));
        org.junit.jupiter.api.Assertions.assertEquals(2, outboxRepository.countByAppointmentId(appointmentId));
	}

	@Test
	void doctorCannotUseReceptionistBookingCommandAndCsrfIsRequired()
			throws Exception {
		UUID organisationId = UUID.randomUUID();
		workspace(
				DOCTOR_TOKEN,
				organisationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"DOCTOR");
		String request = bookingBody(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.now().plusSeconds(86_400));

		mockMvc.perform(post("/api/v1/appointments")
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "booking-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isForbidden());

		workspace(
				RECEPTIONIST_TOKEN,
				organisationId,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");
		mockMvc.perform(post("/api/v1/appointments")
					.cookie(access(RECEPTIONIST_TOKEN))
					.contentType(MediaType.APPLICATION_JSON)
					.content(request))
				.andExpect(status().isForbidden());
	}

	private void workspace(
			String token,
			UUID organisationId,
			UUID userId,
			UUID membershipId,
			String role) {
		when(jwtDecoder.decode(token)).thenReturn(jwt(
				token, userId, organisationId, List.of(role)));
		when(organisationContextClient.resolve(organisationId, token))
				.thenReturn(new OrganisationContextResource(
						membershipId,
						organisationId,
						"Synthetic clinic",
						"CLINIC",
						Set.of(role),
						0));
	}

	private DoctorAvailabilitySchedule schedule(
			UUID organisationId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			LocalDate monday) {
		return DoctorAvailabilitySchedule.create(
				organisationId,
				doctorUserId,
				doctorMembershipId,
				"UTC",
				30,
				0,
				60,
				"Synthetic booking room",
				List.of(new DoctorAvailabilitySchedule.WeeklyWindowValue(
						monday.getDayOfWeek(),
						LocalTime.of(9, 0),
						LocalTime.of(10, 0))),
				List.of(),
				List.of(),
				doctorUserId,
				clock);
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

	private static LocalDate nextMonday() {
		return LocalDate.now(ZoneOffset.UTC)
				.plusDays(1)
				.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
	}

	private static Cookie access(String token) {
		return new Cookie("SAHHA_ACCESS_TOKEN", token);
	}

	private static Cookie csrf() {
		return new Cookie("XSRF-TOKEN", "booking-csrf-token");
	}

	private static String bookingBody(
			UUID bookingRequestId,
			UUID patientRegistrationId,
			UUID doctorUserId,
			Instant startsAt) {
		return """
				{
				  "bookingRequestId":"%s",
				  "patientRegistrationId":"%s",
				  "doctorUserId":"%s",
				  "startsAt":"%s"
				}
				""".formatted(
				bookingRequestId,
				patientRegistrationId,
				doctorUserId,
				startsAt);
	}
}
