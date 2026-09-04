package com.sahha.notification.event;

import java.time.ZoneId;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class AppointmentEventDecoder {

	private static final int SCHEMA_VERSION = 1;
	private static final int MAXIMUM_PAYLOAD_LENGTH = 65_536;

	private final ObjectMapper objectMapper;

	public AppointmentEventDecoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public AppointmentEventV1 decode(String payload) {
		if (payload == null || payload.isBlank()
				|| payload.length() > MAXIMUM_PAYLOAD_LENGTH) {
			throw new InvalidAppointmentEventException("MALFORMED_PAYLOAD");
		}
		AppointmentEventV1 event;
		try {
			event = objectMapper.readValue(payload, AppointmentEventV1.class);
		}
		catch (RuntimeException malformed) {
			throw new InvalidAppointmentEventException("MALFORMED_PAYLOAD");
		}
		validate(event);
		return event;
	}

	private static void validate(AppointmentEventV1 event) {
		if (event == null
				|| event.eventId() == null
				|| event.eventType() == null
				|| event.occurredAt() == null
				|| event.appointmentId() == null
				|| event.organisationId() == null
				|| event.patientId() == null
				|| event.doctorUserId() == null
				|| event.actorUserId() == null
				|| event.status() == null
				|| event.startsAt() == null
				|| event.endsAt() == null
				|| event.resourceVersion() == null) {
			throw new InvalidAppointmentEventException("MISSING_REQUIRED_FIELD");
		}
		if (event.schemaVersion() == null
				|| event.schemaVersion() != SCHEMA_VERSION) {
			throw new InvalidAppointmentEventException("UNSUPPORTED_SCHEMA_VERSION");
		}
		if (event.resourceVersion() < 0) {
			throw new InvalidAppointmentEventException("INVALID_RESOURCE_VERSION");
		}
		if (!event.endsAt().isAfter(event.startsAt())) {
			throw new InvalidAppointmentEventException("INVALID_APPOINTMENT_PERIOD");
		}
		requiredText(event.requestId(), 128, "INVALID_REQUEST_ID");
		requiredText(event.timeZone(), 64, "INVALID_TIME_ZONE");
		requiredText(event.locationLabel(), 160, "INVALID_LOCATION");
		try {
			ZoneId.of(event.timeZone());
		}
		catch (RuntimeException invalidZone) {
			throw new InvalidAppointmentEventException("INVALID_TIME_ZONE");
		}
		if (expectedStatus(event.eventType()) != event.status()) {
			throw new InvalidAppointmentEventException("EVENT_STATUS_MISMATCH");
		}
		boolean initial = event.eventType()
				== AppointmentEventType.APPOINTMENT_REQUESTED;
		if (initial && (event.previousStatus() != null
				|| event.previousStartsAt() != null)) {
			throw new InvalidAppointmentEventException(
					"INVALID_PREVIOUS_SNAPSHOT");
		}
		if (!initial && (event.previousStatus() == null
				|| event.previousStartsAt() == null)) {
			throw new InvalidAppointmentEventException(
					"MISSING_PREVIOUS_SNAPSHOT");
		}
	}

	private static AppointmentStatus expectedStatus(
			AppointmentEventType eventType) {
		return switch (eventType) {
			case APPOINTMENT_REQUESTED -> AppointmentStatus.REQUESTED;
			case APPOINTMENT_CONFIRMED -> AppointmentStatus.CONFIRMED;
			case APPOINTMENT_REJECTED -> AppointmentStatus.REJECTED;
			case APPOINTMENT_RESCHEDULED -> AppointmentStatus.RESCHEDULED;
			case APPOINTMENT_CANCELLED -> AppointmentStatus.CANCELLED;
			case APPOINTMENT_CHECKED_IN -> AppointmentStatus.CHECKED_IN;
			case APPOINTMENT_STARTED -> AppointmentStatus.IN_PROGRESS;
			case APPOINTMENT_COMPLETED -> AppointmentStatus.COMPLETED;
			case APPOINTMENT_NO_SHOW -> AppointmentStatus.NO_SHOW;
		};
	}

	private static void requiredText(
			String value,
			int maximumLength,
			String reasonCode) {
		if (value == null || value.isBlank()
				|| value.length() > maximumLength) {
			throw new InvalidAppointmentEventException(reasonCode);
		}
	}
}
