package com.sahha.scheduling.event;

import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ClinicalEventDecoder {

	public static final String FINALIZED = "consultation.finalised.v1";
	public static final String CORRECTED = "consultation.corrected.v1";
	private static final int SCHEMA_VERSION = 1;
	private static final int MAXIMUM_PAYLOAD_LENGTH = 16_384;

	private final ObjectMapper objectMapper;

	public ClinicalEventDecoder(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public ClinicalConsultationEventV1 decode(String payload) {
		if (payload == null || payload.isBlank()
				|| payload.length() > MAXIMUM_PAYLOAD_LENGTH) {
			throw new InvalidClinicalEventException("MALFORMED_PAYLOAD");
		}
		ClinicalConsultationEventV1 event;
		try {
			event = objectMapper.readValue(
					payload, ClinicalConsultationEventV1.class);
		}
		catch (RuntimeException malformed) {
			throw new InvalidClinicalEventException("MALFORMED_PAYLOAD");
		}
		validate(event);
		return event;
	}

	private static void validate(ClinicalConsultationEventV1 event) {
		if (event == null
				|| event.eventId() == null
				|| event.eventType() == null
				|| event.schemaVersion() == null
				|| event.occurredAt() == null
				|| event.actorUserId() == null
				|| event.organisationId() == null
				|| event.appointmentId() == null
				|| event.consultationId() == null
				|| event.status() == null
				|| event.resourceVersion() == null) {
			throw new InvalidClinicalEventException("MISSING_REQUIRED_FIELD");
		}
		if (event.schemaVersion() != SCHEMA_VERSION) {
			throw new InvalidClinicalEventException("UNSUPPORTED_SCHEMA_VERSION");
		}
		if (!FINALIZED.equals(event.eventType())
				&& !CORRECTED.equals(event.eventType())) {
			throw new InvalidClinicalEventException("UNSUPPORTED_EVENT_TYPE");
		}
		if (!"FINALIZED".equals(event.status())) {
			throw new InvalidClinicalEventException("EVENT_STATUS_MISMATCH");
		}
		if (event.resourceVersion() < 0) {
			throw new InvalidClinicalEventException("INVALID_RESOURCE_VERSION");
		}
	}
}
