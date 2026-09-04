package com.sahha.scheduling.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;

@Component
public class AppointmentEventMapper {

	private static final int SCHEMA_VERSION = 1;

	public Map<String, Object> toPayload(
			UUID eventId,
			AppointmentDomainEventType eventType,
			Appointment appointment,
			AppointmentAuditEvent auditEvent) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", eventId.toString());
		payload.put("eventType", eventType.name());
		payload.put("schemaVersion", SCHEMA_VERSION);
		payload.put("occurredAt", auditEvent.getOccurredAt().toString());
		payload.put("appointmentId", appointment.getId().toString());
		payload.put("organisationId", appointment.getOrganisationId().toString());
		payload.put("patientId", appointment.getPatientId().toString());
		payload.put("doctorUserId", appointment.getDoctorUserId().toString());
		payload.put("actorUserId", auditEvent.getActorUserId().toString());
		payload.put("requestId", auditEvent.getRequestId());
		payload.put("status", appointment.getStatus().name());
		payload.put("startsAt", appointment.getStartsAt().toString());
		payload.put("endsAt", appointment.getEndsAt().toString());
		payload.put("timeZone", appointment.getTimeZone());
		payload.put("locationLabel", appointment.getLocationLabel());
		payload.put("resourceVersion", appointment.getVersion());
		if (auditEvent.getPreviousStatus() != null) {
			payload.put(
					"previousStatus",
					auditEvent.getPreviousStatus().name());
		}
		if (auditEvent.getPreviousStartsAt() != null) {
			payload.put(
					"previousStartsAt",
					auditEvent.getPreviousStartsAt().toString());
		}
		return Map.copyOf(payload);
	}
}
