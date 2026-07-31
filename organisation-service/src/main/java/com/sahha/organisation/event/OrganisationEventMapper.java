package com.sahha.organisation.event;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sahha.organisation.entity.Organisation;
import com.sahha.organisation.entity.OrganisationAuditEvent;

@Component
public class OrganisationEventMapper {

	public Map<String, Object> toPayload(
			Organisation organisation,
			OrganisationAuditEvent event) {
		OrganisationCreatedEventMessage message =
				new OrganisationCreatedEventMessage(
						event.getId(),
						organisation.getId(),
						event.getActorUserId(),
						organisation.getType().name(),
						organisation.getStatus().name(),
						event.getEventType().name(),
						event.getRequestId(),
						event.getOccurredAt());
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("eventId", message.eventId());
		payload.put("organisationId", message.organisationId());
		payload.put("actorUserId", message.actorUserId());
		payload.put("organisationType", message.organisationType());
		payload.put("organisationStatus", message.organisationStatus());
		payload.put("eventType", message.eventType());
		if (message.requestId() != null) {
			payload.put("requestId", message.requestId());
		}
		payload.put("occurredAt", message.occurredAt());
		return Map.copyOf(payload);
	}
}
