package com.sahha.clinical.event;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.clinical.entity.ClinicalAuditEvent;
import com.sahha.clinical.entity.Consultation;

@Component
public class ClinicalEventMapper {

	private static final int SCHEMA_VERSION = 1;

	public Map<String, Object> toPayload(
			UUID eventId,
			ClinicalDomainEventType eventType,
			Consultation consultation,
			ClinicalAuditEvent auditEvent) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", eventId.toString());
		payload.put("eventType", eventType.wireName());
		payload.put("schemaVersion", SCHEMA_VERSION);
		payload.put("occurredAt", auditEvent.getOccurredAt().toString());
		payload.put("actorUserId", auditEvent.getActorUserId().toString());
		payload.put("organisationId", consultation.getOrganisationId().toString());
		payload.put("appointmentId", consultation.getAppointmentId().toString());
		payload.put("consultationId", consultation.getId().toString());
		payload.put("status", consultation.getStatus().name());
		payload.put("resourceVersion", auditEvent.getResourceVersion());
		return Map.copyOf(payload);
	}
}
