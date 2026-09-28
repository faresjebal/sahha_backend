package com.sahha.notification.event;

import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReferralEventDecoder {
    private static final UUID SYSTEM = new UUID(0, 0);
    private final ObjectMapper mapper;
    public ReferralEventDecoder(ObjectMapper mapper) { this.mapper = mapper; }

    public ReferralEventV1 decode(String payload) {
        if (payload == null || payload.isBlank() || payload.length() > 16_384)
            throw new InvalidCommunicationEventException("MALFORMED_PAYLOAD");
        ReferralEventV1 event;
        try {
            event = mapper.readerFor(ReferralEventV1.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(payload);
        } catch (RuntimeException malformed) {
            throw new InvalidCommunicationEventException("MALFORMED_PAYLOAD");
        }
        if (event == null || event.eventId() == null || event.eventType() == null
                || event.organisationId() == null || event.referralId() == null
                || event.actorUserId() == null || event.occurredAt() == null
                || event.resourceVersion() == null || event.recipientUserIds() == null)
            throw new InvalidCommunicationEventException("MISSING_REQUIRED_FIELD");
        if (event.schemaVersion() == null || event.schemaVersion() != 1)
            throw new InvalidCommunicationEventException("UNSUPPORTED_SCHEMA_VERSION");
        if (event.resourceVersion() < 0)
            throw new InvalidCommunicationEventException("INVALID_RESOURCE_VERSION");
        if (!event.expectedStatus().equals(event.status()))
            throw new InvalidCommunicationEventException("INVALID_LIFECYCLE_STATUS");
        boolean expiry = "EXPIRED".equals(event.status());
        if (SYSTEM.equals(event.eventId()) || SYSTEM.equals(event.organisationId())
                || SYSTEM.equals(event.referralId()) || expiry != SYSTEM.equals(event.actorUserId()))
            throw new InvalidCommunicationEventException("INVALID_ACTOR_OR_RESOURCE");
        var recipients = event.recipientUserIds();
        if (recipients.size() != (expiry ? 2 : 1)
                || recipients.stream().anyMatch(id -> id == null || SYSTEM.equals(id))
                || new HashSet<>(recipients).size() != recipients.size()
                || recipients.contains(event.actorUserId()))
            throw new InvalidCommunicationEventException("INVALID_RECIPIENTS");
        return event;
    }
}
