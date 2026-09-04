package com.sahha.scheduling.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.AppointmentAuditEventType;
import com.sahha.scheduling.entity.AppointmentStatus;

class AppointmentEventMapperTests {

	private static final Instant OCCURRED_AT =
			Instant.parse("2027-01-01T00:00:00Z");
	private static final Clock CLOCK = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);

	@Test
	void transitionPayloadIsVersionedAndMinimised() {
		Appointment appointment = appointment();
		AppointmentStatus previousStatus = appointment.getStatus();
		Instant previousStartsAt = appointment.getStartsAt();
		appointment.cancel("Sensitive patient explanation", CLOCK);
		AppointmentAuditEvent auditEvent = AppointmentAuditEvent.transitioned(
				appointment,
				previousStatus,
				previousStartsAt,
				AppointmentAuditEventType.APPOINTMENT_CANCELLED,
				UUID.randomUUID(),
				"Sensitive patient explanation",
				UUID.randomUUID(),
				UUID.randomUUID(),
				"event-mapper-test");
		UUID eventId = UUID.randomUUID();

		Map<String, Object> payload = new AppointmentEventMapper().toPayload(
				eventId,
				AppointmentDomainEventType.APPOINTMENT_CANCELLED,
				appointment,
				auditEvent);

		assertEquals(eventId.toString(), payload.get("eventId"));
		assertEquals("APPOINTMENT_CANCELLED", payload.get("eventType"));
		assertEquals(1, payload.get("schemaVersion"));
		assertEquals("REQUESTED", payload.get("previousStatus"));
		assertEquals("CANCELLED", payload.get("status"));
		assertEquals(previousStartsAt.toString(), payload.get("previousStartsAt"));
		assertFalse(payload.containsKey("reason"));
		assertFalse(payload.containsKey("statusReason"));
		assertFalse(payload.containsKey("patientRegistrationId"));
		assertFalse(payload.containsKey("doctorMembershipId"));
		assertFalse(payload.containsKey("actorMembershipId"));
		assertFalse(payload.containsValue("Sensitive patient explanation"));
	}

	@Test
	void checkedInPayloadUsesTheCareDeliveryEventWithoutPatientDetails() {
		Appointment appointment = appointment();
		appointment.confirm(CLOCK);
		AppointmentStatus previousStatus = appointment.getStatus();
		appointment.checkIn(CLOCK);
		AppointmentAuditEvent auditEvent = AppointmentAuditEvent.transitioned(
				appointment,
				previousStatus,
				appointment.getStartsAt(),
				AppointmentAuditEventType.APPOINTMENT_CHECKED_IN,
				UUID.randomUUID(),
				null,
				UUID.randomUUID(),
				UUID.randomUUID(),
				"checked-in-event-test");

		Map<String, Object> payload = new AppointmentEventMapper().toPayload(
				UUID.randomUUID(),
				AppointmentDomainEventType.APPOINTMENT_CHECKED_IN,
				appointment,
				auditEvent);

		assertEquals("APPOINTMENT_CHECKED_IN", payload.get("eventType"));
		assertEquals("CONFIRMED", payload.get("previousStatus"));
		assertEquals("CHECKED_IN", payload.get("status"));
		assertFalse(payload.containsKey("patientRegistrationId"));
		assertFalse(payload.containsKey("transitionReason"));
		assertFalse(payload.containsKey("actorMembershipId"));
	}

	private static Appointment appointment() {
		return Appointment.request(
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				Instant.parse("2027-01-04T09:00:00Z"),
				Instant.parse("2027-01-04T09:30:00Z"),
				"UTC",
				"Synthetic room",
				UUID.randomUUID(),
				UUID.randomUUID(),
				CLOCK);
	}
}
