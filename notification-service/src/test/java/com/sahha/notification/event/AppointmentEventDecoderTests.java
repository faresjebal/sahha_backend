package com.sahha.notification.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class AppointmentEventDecoderTests {

	@Autowired
	private AppointmentEventDecoder decoder;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void decodesTheSchedulingVersionOneContract() {
		AppointmentEventV1 expected = event(
				AppointmentEventType.APPOINTMENT_REQUESTED,
				AppointmentStatus.REQUESTED,
				1,
				null,
				null);

		AppointmentEventV1 decoded = decoder.decode(
				objectMapper.writeValueAsString(expected));

		assertEquals(expected, decoded);
	}

	@Test
	void rejectsMalformedAndUnsupportedPayloadsWithSafeReasonCodes() {
		InvalidAppointmentEventException malformed = assertThrows(
				InvalidAppointmentEventException.class,
				() -> decoder.decode("{not-json"));
		assertEquals("MALFORMED_PAYLOAD", malformed.getReasonCode());

		AppointmentEventV1 unsupported = event(
				AppointmentEventType.APPOINTMENT_REQUESTED,
				AppointmentStatus.REQUESTED,
				2,
				null,
				null);
		InvalidAppointmentEventException schema = assertThrows(
				InvalidAppointmentEventException.class,
				() -> decoder.decode(
						objectMapper.writeValueAsString(unsupported)));
		assertEquals("UNSUPPORTED_SCHEMA_VERSION", schema.getReasonCode());
	}

	@Test
	void rejectsStatusAndTransitionSnapshotContractMismatches() {
		AppointmentEventV1 wrongStatus = event(
				AppointmentEventType.APPOINTMENT_CANCELLED,
				AppointmentStatus.CONFIRMED,
				1,
				AppointmentStatus.REQUESTED,
				Instant.parse("2027-01-04T09:00:00Z"));
		InvalidAppointmentEventException mismatch = assertThrows(
				InvalidAppointmentEventException.class,
				() -> decoder.decode(
						objectMapper.writeValueAsString(wrongStatus)));
		assertEquals("EVENT_STATUS_MISMATCH", mismatch.getReasonCode());

		AppointmentEventV1 missingPrevious = event(
				AppointmentEventType.APPOINTMENT_CONFIRMED,
				AppointmentStatus.CONFIRMED,
				1,
				null,
				null);
		InvalidAppointmentEventException previous = assertThrows(
				InvalidAppointmentEventException.class,
				() -> decoder.decode(
						objectMapper.writeValueAsString(missingPrevious)));
		assertEquals("MISSING_PREVIOUS_SNAPSHOT", previous.getReasonCode());
	}

	@Test
	void decodesCareDeliveryLifecycleEventsWithExactStatuses() {
		assertLifecycleEvent(
				AppointmentEventType.APPOINTMENT_CHECKED_IN,
				AppointmentStatus.CHECKED_IN,
				AppointmentStatus.CONFIRMED);
		assertLifecycleEvent(
				AppointmentEventType.APPOINTMENT_STARTED,
				AppointmentStatus.IN_PROGRESS,
				AppointmentStatus.CHECKED_IN);
		assertLifecycleEvent(
				AppointmentEventType.APPOINTMENT_COMPLETED,
				AppointmentStatus.COMPLETED,
				AppointmentStatus.IN_PROGRESS);
		assertLifecycleEvent(
				AppointmentEventType.APPOINTMENT_NO_SHOW,
				AppointmentStatus.NO_SHOW,
				AppointmentStatus.CONFIRMED);
	}

	private void assertLifecycleEvent(
			AppointmentEventType type,
			AppointmentStatus status,
			AppointmentStatus previousStatus) {
		AppointmentEventV1 expected = event(
				type,
				status,
				1,
				previousStatus,
				Instant.parse("2027-01-04T09:00:00Z"));
		assertEquals(
				expected,
				decoder.decode(objectMapper.writeValueAsString(expected)));
	}

	private static AppointmentEventV1 event(
			AppointmentEventType type,
			AppointmentStatus status,
			int schemaVersion,
			AppointmentStatus previousStatus,
			Instant previousStartsAt) {
		return new AppointmentEventV1(
				UUID.randomUUID(),
				type,
				schemaVersion,
				Instant.parse("2027-01-01T00:00:00Z"),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				UUID.randomUUID(),
				"notification-decoder-test",
				status,
				Instant.parse("2027-01-04T09:00:00Z"),
				Instant.parse("2027-01-04T09:30:00Z"),
				"UTC",
				"Synthetic notification room",
				0L,
				previousStatus,
				previousStartsAt);
	}
}
