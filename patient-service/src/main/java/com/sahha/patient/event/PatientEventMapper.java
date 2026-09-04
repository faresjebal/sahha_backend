package com.sahha.patient.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sahha.patient.entity.PatientAuditEvent;
import com.sahha.patient.entity.PatientAccountLink;
import com.sahha.patient.entity.PatientOrganisationRegistration;

@Component
public class PatientEventMapper {

	public Map<String, Object> toPayload(
			PatientOrganisationRegistration registration,
			PatientAuditEvent event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", event.getId());
		payload.put("organisationId", registration.getOrganisationId());
		payload.put("registrationId", registration.getId());
		payload.put("patientId", registration.getPatient().getId());
		payload.put("actorUserId", event.getActorUserId());
		payload.put("registrationStatus", registration.getStatus().name());
		payload.put("resourceVersion", registration.getVersion());
		payload.put("eventType", event.getEventType().name());
		if (event.getRequestId() != null) {
			payload.put("requestId", event.getRequestId());
		}
		payload.put("occurredAt", event.getOccurredAt());
		return Map.copyOf(payload);
	}

	public Map<String, Object> toAccountLinkPayload(
			PatientAccountLink link,
			PatientAuditEvent event) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", event.getId());
		payload.put("organisationId", event.getOrganisationId());
		payload.put("patientId", link.getPatientId());
		payload.put("authUserId", link.getAuthUserId());
		payload.put("eventType", event.getEventType().name());
		if (event.getRequestId() != null) {
			payload.put("requestId", event.getRequestId());
		}
		payload.put("occurredAt", event.getOccurredAt());
		return Map.copyOf(payload);
	}
}
