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
import java.util.Set;
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
import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.client.organisation.SchedulingDoctorDirectoryClient;
import com.sahha.scheduling.client.organisation.SchedulingDoctorResource;
import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;
import com.sahha.scheduling.repository.AppointmentAuditEventRepository;
import com.sahha.scheduling.repository.AppointmentRepository;
import com.sahha.scheduling.repository.AppointmentOutboxEventRepository;
import com.sahha.scheduling.repository.DoctorAvailabilityScheduleRepository;
import com.sahha.scheduling.repository.ConsumedClinicalEventRepository;
import com.sahha.scheduling.outbox.AppointmentOutboxRecorder;
import com.sahha.scheduling.event.ClinicalConsultationEventV1;
import com.sahha.scheduling.event.ClinicalEventDecoder;
import com.sahha.scheduling.event.ClinicalEventSource;
import com.sahha.scheduling.service.appointmentservice.ClinicalAppointmentCompletionService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AppointmentLifecycleHttpIntegrationTests {

	private static final String DOCTOR_TOKEN = "lifecycle.doctor.token";
	private static final String OTHER_DOCTOR_TOKEN = "lifecycle.other-doctor.token";
	private static final String RECEPTIONIST_TOKEN =
			"lifecycle.receptionist.token";

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
	private AppointmentOutboxRecorder outboxRecorder;

	@Autowired
	private ClinicalAppointmentCompletionService clinicalCompletionService;

	@Autowired
	private ConsumedClinicalEventRepository consumedClinicalEventRepository;

	@Autowired
	private Clock clock;

	@MockitoBean
	private JwtDecoder jwtDecoder;

	@MockitoBean
	private OrganisationContextClient organisationContextClient;

	@MockitoBean
	private SchedulingDoctorDirectoryClient doctorDirectoryClient;

	@Test
	void doctorReadsOwnAppointmentAndConfirmsIdempotently() throws Exception {
		Fixture fixture = fixture();
		workspace(
				DOCTOR_TOKEN,
				fixture.organisationId(),
				fixture.doctorUserId(),
				fixture.doctorMembershipId(),
				"DOCTOR");

		mockMvc.perform(get("/api/v1/appointments")
					.cookie(access(DOCTOR_TOKEN))
					.param("from", fixture.startsAt().minusSeconds(60).toString())
					.param("to", fixture.startsAt().plusSeconds(3_600).toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id")
						.value(fixture.appointmentId().toString()));

		UUID commandRequestId = UUID.randomUUID();
		String command = command(commandRequestId, 0);
		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/confirm",
				fixture.appointmentId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.header("X-Request-ID", "confirm-appointment-http")
					.contentType(MediaType.APPLICATION_JSON)
					.content(command))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.statusReason").isEmpty())
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/confirm",
				fixture.appointmentId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(command))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.version").value(1));

		org.junit.jupiter.api.Assertions.assertEquals(
				2, auditRepository.countByAppointmentId(fixture.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				2, outboxRepository.countByAppointmentId(fixture.appointmentId()));
	}

	@Test
	void unrelatedDoctorAndReceptionistCannotMakeDoctorDecision()
			throws Exception {
		Fixture fixture = fixture();
		workspace(
				OTHER_DOCTOR_TOKEN,
				fixture.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				fixture.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");

		mockMvc.perform(get(
				"/api/v1/appointments/{appointmentId}",
				fixture.appointmentId())
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());

		for (String command : List.of("confirm", "reject")) {
			String body = command.equals("confirm")
					? command(UUID.randomUUID(), 0)
					: reasonedCommand(UUID.randomUUID(), 0, "Doctor decision required");
			mockMvc.perform(post(
					"/api/v1/appointments/{appointmentId}/{command}",
					fixture.appointmentId(),
					command)
						.cookie(access(RECEPTIONIST_TOKEN), csrf())
						.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
					.andExpect(status().isForbidden());
		}
	}

	@Test
	void clinicalContextReturnsOnlyTheOwningDoctorsMinimalAppointmentData()
			throws Exception {
		Fixture fixture = fixture();
		workspace(
				DOCTOR_TOKEN,
				fixture.organisationId(),
				fixture.doctorUserId(),
				fixture.doctorMembershipId(),
				"DOCTOR");
		Appointment appointment = appointmentRepository
				.findById(fixture.appointmentId()).orElseThrow();
		appointment.confirm(clock);
		appointment.checkIn(clock);
		appointment.start(clock);
		appointmentRepository.saveAndFlush(appointment);

		mockMvc.perform(get(
				"/api/v1/internal/clinical/appointments/{appointmentId}",
				fixture.appointmentId())
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appointmentId")
						.value(fixture.appointmentId().toString()))
				.andExpect(jsonPath("$.organisationId")
						.value(fixture.organisationId().toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(fixture.doctorUserId().toString()))
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.statusReason").doesNotExist())
				.andExpect(jsonPath("$.locationLabel").doesNotExist())
				.andExpect(jsonPath("$.bookedByUserId").doesNotExist());
	}

	@Test
	void clinicalContextRejectsAnUnrelatedDoctorAndReceptionist()
			throws Exception {
		Fixture fixture = fixture();
		workspace(
				OTHER_DOCTOR_TOKEN,
				fixture.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				fixture.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");

		mockMvc.perform(get(
				"/api/v1/internal/clinical/appointments/{appointmentId}",
				fixture.appointmentId())
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isForbidden());
		mockMvc.perform(get(
				"/api/v1/internal/clinical/appointments/{appointmentId}",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	@Test
	void clinicalPatientAccessRequiresAnActiveOwnedCareRelationship()
			throws Exception {
		Fixture fixture = fixture();
		UUID unrelatedDoctorId = UUID.randomUUID();
		workspace(
				DOCTOR_TOKEN,
				fixture.organisationId(),
				fixture.doctorUserId(),
				fixture.doctorMembershipId(),
				"DOCTOR");
		workspace(
				OTHER_DOCTOR_TOKEN,
				fixture.organisationId(),
				unrelatedDoctorId,
				UUID.randomUUID(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				fixture.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");
		Appointment appointment = appointmentRepository
				.findById(fixture.appointmentId()).orElseThrow();
		UUID patientRegistrationId = appointment.getPatientRegistrationId();
		appointment.confirm(clock);
		appointmentRepository.saveAndFlush(appointment);

		mockMvc.perform(get(
				"/api/v1/internal/clinical/patients/{patientRegistrationId}/access-context",
				patientRegistrationId)
					.cookie(access(DOCTOR_TOKEN)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.appointmentId")
						.value(fixture.appointmentId().toString()))
				.andExpect(jsonPath("$.patientRegistrationId")
						.value(patientRegistrationId.toString()))
				.andExpect(jsonPath("$.doctorUserId")
						.value(fixture.doctorUserId().toString()))
				.andExpect(jsonPath("$.status").value("CONFIRMED"))
				.andExpect(jsonPath("$.locationLabel").doesNotExist());

		mockMvc.perform(get(
				"/api/v1/internal/clinical/patients/{patientRegistrationId}/access-context",
				patientRegistrationId)
					.cookie(access(OTHER_DOCTOR_TOKEN)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get(
				"/api/v1/internal/clinical/patients/{patientRegistrationId}/access-context",
				patientRegistrationId)
					.cookie(access(RECEPTIONIST_TOKEN)))
				.andExpect(status().isForbidden());
	}

	@Test
	void clinicalFinalizationCompletesOnceAndPersistsConflictEvidence()
			throws Exception {
		Fixture fixture = fixture();
		Appointment appointment = appointmentRepository
				.findById(fixture.appointmentId()).orElseThrow();
		appointment.confirm(clock);
		appointment.checkIn(clock);
		appointment.start(clock);
		appointmentRepository.saveAndFlush(appointment);
		ClinicalConsultationEventV1 event = clinicalEvent(fixture, UUID.randomUUID());

		var applied = clinicalCompletionService.process(
				event,
				ClinicalEventSource.kafka(
						"sahha.clinical.consultations.v1", 0, 10),
				null);
		var duplicate = clinicalCompletionService.process(
				event,
				ClinicalEventSource.kafka(
						"sahha.clinical.consultations.v1", 0, 10),
				null);

		org.junit.jupiter.api.Assertions.assertEquals("APPLIED",
				applied.outcome().name());
		org.junit.jupiter.api.Assertions.assertTrue(duplicate.duplicate());
		org.junit.jupiter.api.Assertions.assertEquals(
				"COMPLETED",
				appointmentRepository.findById(fixture.appointmentId())
						.orElseThrow().getStatus().name());
		org.junit.jupiter.api.Assertions.assertEquals(
				1, consumedClinicalEventRepository
						.countByAppointmentId(fixture.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				2, auditRepository.countByAppointmentId(fixture.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				2, outboxRepository.countByAppointmentId(fixture.appointmentId()));
		var completedEvent = outboxRepository.findAll().stream()
				.filter(candidate -> candidate.getAppointmentId().equals(
						fixture.appointmentId()))
				.filter(candidate -> "APPOINTMENT_COMPLETED".equals(
						candidate.getEventType().name()))
				.findFirst().orElseThrow();
		org.junit.jupiter.api.Assertions.assertEquals(
				"COMPLETED", completedEvent.getPayload().get("status"));
		for (String clinicalField : List.of(
				"consultationId", "diagnoses", "medications", "clinicalAssessment",
				"treatmentPlan", "additionalNotes")) {
			org.junit.jupiter.api.Assertions.assertFalse(
					completedEvent.getPayload().containsKey(clinicalField));
		}

		Fixture conflictFixture = fixture();
		var conflict = clinicalCompletionService.process(
				clinicalEvent(conflictFixture, UUID.randomUUID()),
				ClinicalEventSource.kafka(
						"sahha.clinical.consultations.v1", 0, 11),
				null);
		org.junit.jupiter.api.Assertions.assertEquals(
				"CONFLICT", conflict.outcome().name());
		org.junit.jupiter.api.Assertions.assertEquals(
				"APPOINTMENT_NOT_IN_PROGRESS", conflict.conflictCode());
		org.junit.jupiter.api.Assertions.assertEquals(
				"REQUESTED",
				appointmentRepository.findById(conflictFixture.appointmentId())
						.orElseThrow().getStatus().name());
	}

	@Test
	void owningDoctorCanRecoverCompletionThroughTheInternalContract()
			throws Exception {
		Fixture fixture = fixture();
		workspace(
				DOCTOR_TOKEN,
				fixture.organisationId(),
				fixture.doctorUserId(),
				fixture.doctorMembershipId(),
				"DOCTOR");
		Appointment appointment = appointmentRepository
				.findById(fixture.appointmentId()).orElseThrow();
		appointment.confirm(clock);
		appointment.checkIn(clock);
		appointment.start(clock);
		appointmentRepository.saveAndFlush(appointment);
		UUID eventId = UUID.randomUUID();
		UUID consultationId = UUID.randomUUID();

		mockMvc.perform(post(
				"/api/v1/internal/clinical/appointments/{appointmentId}/completion-recovery",
				fixture.appointmentId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "eventId":"%s",
							  "consultationId":"%s",
							  "clinicalResourceVersion":2,
							  "occurredAt":"%s"
							}
							""".formatted(
							eventId, consultationId, clock.instant())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(eventId.toString()))
				.andExpect(jsonPath("$.outcome").value("APPLIED"))
				.andExpect(jsonPath("$.appointmentStatus").value("COMPLETED"));
	}

	@Test
	void receptionistReschedulesThenCancelsWithReasonsAndVersions()
			throws Exception {
		Fixture fixture = fixture();
		UUID receptionistUserId = UUID.randomUUID();
		workspace(
				RECEPTIONIST_TOKEN,
				fixture.organisationId(),
				receptionistUserId,
				UUID.randomUUID(),
				"RECEPTIONIST");
		when(doctorDirectoryClient.findActiveDoctor(
				fixture.organisationId(),
				fixture.doctorUserId(),
				RECEPTIONIST_TOKEN))
				.thenReturn(new SchedulingDoctorResource(
						fixture.doctorMembershipId(),
						fixture.organisationId(),
						fixture.doctorUserId(),
						"Dr Synthetic Lifecycle",
						0));

		Instant movedTo = fixture.startsAt().plusSeconds(1_800);
		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/reschedule",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(rescheduleCommand(
							UUID.randomUUID(), 0, movedTo,
							"Patient requested another time")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESCHEDULED"))
				.andExpect(jsonPath("$.statusReason")
						.value("Patient requested another time"))
				.andExpect(jsonPath("$.startsAt").value(movedTo.toString()))
				.andExpect(jsonPath("$.version").value(1));

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/cancel",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(reasonedCommand(
							UUID.randomUUID(), 0, "Stale command")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:concurrent-appointment-modification"));

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/cancel",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(reasonedCommand(UUID.randomUUID(), 1, "Patient cancelled")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"))
				.andExpect(jsonPath("$.statusReason").value("Patient cancelled"))
				.andExpect(jsonPath("$.version").value(2));

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/cancel",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(reasonedCommand(UUID.randomUUID(), 2, " ")))
				.andExpect(status().isBadRequest());

		org.junit.jupiter.api.Assertions.assertEquals(
				3, auditRepository.countByAppointmentId(fixture.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				3, outboxRepository.countByAppointmentId(fixture.appointmentId()));
	}

	@Test
	void receptionistChecksInThenOwningDoctorStartsAndCompletes()
			throws Exception {
		Fixture fixture = fixture();
		UUID receptionistUserId = UUID.randomUUID();
		workspace(
				DOCTOR_TOKEN,
				fixture.organisationId(),
				fixture.doctorUserId(),
				fixture.doctorMembershipId(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				fixture.organisationId(),
				receptionistUserId,
				UUID.randomUUID(),
				"RECEPTIONIST");

		performCommand(
				DOCTOR_TOKEN,
				fixture.appointmentId(),
				"confirm",
				command(UUID.randomUUID(), 0),
				"CONFIRMED",
				1);

		UUID checkInCommandId = UUID.randomUUID();
		String checkInCommand = command(checkInCommandId, 1);
		performCommand(
				RECEPTIONIST_TOKEN,
				fixture.appointmentId(),
				"check-in",
				checkInCommand,
				"CHECKED_IN",
				2);
		performCommand(
				RECEPTIONIST_TOKEN,
				fixture.appointmentId(),
				"check-in",
				checkInCommand,
				"CHECKED_IN",
				2);

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/start",
				fixture.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(command(UUID.randomUUID(), 2)))
				.andExpect(status().isForbidden());

		performCommand(
				DOCTOR_TOKEN,
				fixture.appointmentId(),
				"start",
				command(UUID.randomUUID(), 2),
				"IN_PROGRESS",
				3);
		performCommand(
				DOCTOR_TOKEN,
				fixture.appointmentId(),
				"complete",
				command(UUID.randomUUID(), 3),
				"COMPLETED",
				4);

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/no-show",
				fixture.appointmentId())
					.cookie(access(DOCTOR_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(command(UUID.randomUUID(), 4)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:invalid-appointment-transition"));

		org.junit.jupiter.api.Assertions.assertEquals(
				5, auditRepository.countByAppointmentId(fixture.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				5, outboxRepository.countByAppointmentId(fixture.appointmentId()));
	}

	@Test
	void noShowCannotBeRecordedBeforeStartAndIsIdempotentAfterStart()
			throws Exception {
		Fixture future = fixture();
		workspace(
				DOCTOR_TOKEN,
				future.organisationId(),
				future.doctorUserId(),
				future.doctorMembershipId(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				future.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");
		performCommand(
				DOCTOR_TOKEN,
				future.appointmentId(),
				"confirm",
				command(UUID.randomUUID(), 0),
				"CONFIRMED",
				1);

		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/no-show",
				future.appointmentId())
					.cookie(access(RECEPTIONIST_TOKEN), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.contentType(MediaType.APPLICATION_JSON)
					.content(command(UUID.randomUUID(), 1)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.type").value(
						"urn:sahha:problem:appointment-not-ready-for-no-show"));

		Fixture past = fixture(clock.instant().minusSeconds(3_600));
		workspace(
				DOCTOR_TOKEN,
				past.organisationId(),
				past.doctorUserId(),
				past.doctorMembershipId(),
				"DOCTOR");
		workspace(
				RECEPTIONIST_TOKEN,
				past.organisationId(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"RECEPTIONIST");
		performCommand(
				DOCTOR_TOKEN,
				past.appointmentId(),
				"confirm",
				command(UUID.randomUUID(), 0),
				"CONFIRMED",
				1);
		UUID noShowCommandId = UUID.randomUUID();
		String noShowCommand = command(noShowCommandId, 1);
		performCommand(
				RECEPTIONIST_TOKEN,
				past.appointmentId(),
				"no-show",
				noShowCommand,
				"NO_SHOW",
				2);
		performCommand(
				RECEPTIONIST_TOKEN,
				past.appointmentId(),
				"no-show",
				noShowCommand,
				"NO_SHOW",
				2);

		org.junit.jupiter.api.Assertions.assertEquals(
				3, auditRepository.countByAppointmentId(past.appointmentId()));
		org.junit.jupiter.api.Assertions.assertEquals(
				3, outboxRepository.countByAppointmentId(past.appointmentId()));
	}

	private Fixture fixture() {
		LocalDate monday = nextMonday();
		return fixture(monday.atTime(9, 0).toInstant(ZoneOffset.UTC));
	}

	private Fixture fixture(Instant startsAt) {
		UUID organisationId = UUID.randomUUID();
		UUID doctorUserId = UUID.randomUUID();
		UUID doctorMembershipId = UUID.randomUUID();
		UUID bookingActorId = UUID.randomUUID();
		UUID bookingMembershipId = UUID.randomUUID();
		LocalDate scheduleDate = nextMonday();
		DoctorAvailabilitySchedule schedule = availabilityRepository.saveAndFlush(
				DoctorAvailabilitySchedule.create(
						organisationId,
						doctorUserId,
						doctorMembershipId,
						"UTC",
						30,
						0,
						60,
						"Synthetic lifecycle room",
						List.of(new DoctorAvailabilitySchedule.WeeklyWindowValue(
								scheduleDate.getDayOfWeek(),
								LocalTime.of(9, 0),
								LocalTime.of(11, 0))),
						List.of(),
						List.of(),
						doctorUserId,
						clock));
		Appointment appointment = appointmentRepository.saveAndFlush(
				Appointment.request(
						organisationId,
						UUID.randomUUID(),
						UUID.randomUUID(),
						UUID.randomUUID(),
						doctorUserId,
						doctorMembershipId,
						schedule.getId(),
						startsAt,
						startsAt.plusSeconds(1_800),
						"UTC",
						"Synthetic lifecycle room",
						bookingActorId,
						bookingMembershipId,
						clock));
		AppointmentAuditEvent bookingAudit = auditRepository.saveAndFlush(
				AppointmentAuditEvent.booked(
				appointment,
				bookingActorId,
				bookingMembershipId,
				"lifecycle-fixture"));
		outboxRecorder.record(appointment, bookingAudit);
		return new Fixture(
				organisationId,
				doctorUserId,
				doctorMembershipId,
				appointment.getId(),
				startsAt);
	}

	private void performCommand(
			String token,
			UUID appointmentId,
			String action,
			String body,
			String expectedStatus,
			long expectedVersion) throws Exception {
		mockMvc.perform(post(
				"/api/v1/appointments/{appointmentId}/{action}",
				appointmentId,
				action)
					.cookie(access(token), csrf())
					.header("X-XSRF-TOKEN", "lifecycle-csrf-token")
					.header("X-Request-ID", action + "-appointment-http")
					.contentType(MediaType.APPLICATION_JSON)
					.content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value(expectedStatus))
				.andExpect(jsonPath("$.version").value(expectedVersion));
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
		return new Cookie("XSRF-TOKEN", "lifecycle-csrf-token");
	}

	private static String command(UUID commandRequestId, long version) {
		return """
				{
				  "commandRequestId":"%s",
				  "version":%d
				}
				""".formatted(commandRequestId, version);
	}

	private static String reasonedCommand(
			UUID commandRequestId,
			long version,
			String reason) {
		return """
				{
				  "commandRequestId":"%s",
				  "version":%d,
				  "reason":"%s"
				}
				""".formatted(commandRequestId, version, reason);
	}

	private static String rescheduleCommand(
			UUID commandRequestId,
			long version,
			Instant startsAt,
			String reason) {
		return """
				{
				  "commandRequestId":"%s",
				  "version":%d,
				  "startsAt":"%s",
				  "reason":"%s"
				}
				""".formatted(commandRequestId, version, startsAt, reason);
	}

	private ClinicalConsultationEventV1 clinicalEvent(
			Fixture fixture, UUID eventId) {
		return new ClinicalConsultationEventV1(
				eventId,
				ClinicalEventDecoder.FINALIZED,
				1,
				clock.instant(),
				fixture.doctorUserId(),
				fixture.organisationId(),
				fixture.appointmentId(),
				UUID.randomUUID(),
				"FINALIZED",
				2L);
	}

	private record Fixture(
			UUID organisationId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			UUID appointmentId,
			Instant startsAt) {
	}
}
