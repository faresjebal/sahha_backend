package com.sahha.file.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.file.entity.MedicalFile;

@Component
public class FileEventMapper {

	public Map<String, Object> scanDecision(
			MedicalFile file,
			String eventType,
			Instant occurredAt) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", UUID.randomUUID().toString());
		payload.put("eventType", eventType);
		payload.put("schemaVersion", 1);
		payload.put("occurredAt", occurredAt.toString());
		payload.put("organisationId", file.getOrganisationId().toString());
		payload.put("medicalFileId", file.getId().toString());
		payload.put("consultationId", file.getConsultationId().toString());
		payload.put("patientRegistrationId",
				file.getPatientRegistrationId().toString());
		payload.put("scanStatus", file.getScanStatus().name());
		payload.put("resourceVersion", file.getVersion());
		return Map.copyOf(payload);
	}
}
