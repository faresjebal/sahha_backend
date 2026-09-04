package com.sahha.scheduling.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ClinicalEventDecoderTests {

	private final ClinicalEventDecoder decoder =
			new ClinicalEventDecoder(JsonMapper.builder().build());

	@Test
	void decodesTheMinimumVersionedFinalizationEnvelope() {
		UUID eventId = UUID.randomUUID();
		UUID consultationId = UUID.randomUUID();
		ClinicalConsultationEventV1 event = decoder.decode(
				json(eventId, consultationId, "consultation.finalised.v1", 1));

		assertEquals(eventId, event.eventId());
		assertEquals(consultationId, event.consultationId());
		assertEquals("FINALIZED", event.status());
	}

	@Test
	void rejectsUnknownSchemaAndClinicalContentSizedPayloads() {
		String unknownSchema = json(
				UUID.randomUUID(), UUID.randomUUID(),
				"consultation.finalised.v1", 2);
		assertEquals(
				"UNSUPPORTED_SCHEMA_VERSION",
				assertThrows(InvalidClinicalEventException.class,
						() -> decoder.decode(unknownSchema)).getReasonCode());
		assertEquals(
				"MALFORMED_PAYLOAD",
				assertThrows(InvalidClinicalEventException.class,
						() -> decoder.decode("x".repeat(16_385))).getReasonCode());
	}

	private static String json(
			UUID eventId,
			UUID consultationId,
			String eventType,
			int schemaVersion) {
		return """
				{
				  "eventId":"%s",
				  "eventType":"%s",
				  "schemaVersion":%d,
				  "occurredAt":"2026-08-24T21:00:00Z",
				  "actorUserId":"%s",
				  "organisationId":"%s",
				  "appointmentId":"%s",
				  "consultationId":"%s",
				  "status":"FINALIZED",
				  "resourceVersion":2
				}
				""".formatted(
				eventId, eventType, schemaVersion, UUID.randomUUID(),
				UUID.randomUUID(), UUID.randomUUID(), consultationId);
	}
}
