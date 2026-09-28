package com.sahha.notification.event;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

class ReferralEventDecoderTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReferralEventDecoder decoder = new ReferralEventDecoder(mapper);
    static Map<String,Object> payload() {
        return new LinkedHashMap<>(Map.of("eventId", UUID.randomUUID().toString(),
                "eventType", "referral.sent.v1", "schemaVersion", 1,
                "occurredAt", Instant.now().toString(), "organisationId", UUID.randomUUID().toString(),
                "referralId", UUID.randomUUID().toString(), "actorUserId", UUID.randomUUID().toString(),
                "recipientUserIds", List.of(UUID.randomUUID().toString()), "status", "SENT", "resourceVersion", 0L));
    }
    @ParameterizedTest
    @CsvSource({"sent,SENT,REFERRAL_RECEIVED", "accepted,ACTIVE,REFERRAL_ACCEPTED",
            "rejected,REJECTED,REFERRAL_REJECTED", "revoked,REVOKED,REFERRAL_REVOKED",
            "completed,COMPLETED,REFERRAL_COMPLETED", "expired,EXPIRED,REFERRAL_EXPIRED"})
    void acceptsOnlyExistingLifecycleContracts(String type, String status, String notificationType) {
        var value = payload(); value.put("eventType", "referral." + type + ".v1"); value.put("status", status);
        if (type.equals("expired")) {
            value.put("actorUserId", new UUID(0, 0).toString());
            value.put("recipientUserIds", List.of(UUID.randomUUID().toString(), UUID.randomUUID().toString()));
        }
        var result = decoder.decode(mapper.writeValueAsString(value));
        assertEquals(notificationType, result.notificationType().name());
        assertEquals(status, result.expectedStatus());
    }
    static Stream<Map<String,Object>> invalid() {
        return Stream.of(
            Map.of("eventType", "referral.drafted.v1"), Map.of("schemaVersion", 2),
            Map.of("status", "ACTIVE"), Map.of("resourceVersion", -1),
            Map.of("recipientUserIds", List.of()), Map.of("recipientUserIds", List.of(UUID.randomUUID(), UUID.randomUUID())),
            Map.of("actorUserId", new UUID(0, 0)), Map.of("referralId", "invalid"),
            Map.of("patientId", UUID.randomUUID()), Map.of("reason", "Synthetic confidential reason"),
            Map.of("selectedItems", List.of()), Map.of("sourceConsultationId", UUID.randomUUID()));
    }
    @ParameterizedTest @MethodSource("invalid")
    void rejectsMalformedScopeAndPrivatePayloadAdditions(Map<String,Object> changes) {
        var value = payload(); value.putAll(changes);
        assertThrows(InvalidCommunicationEventException.class, () -> decoder.decode(mapper.writeValueAsString(value)));
    }
    @Test void rejectsMissingFieldsSelfRecipientNullAndOversizedInput() {
        var value = payload(); value.remove("organisationId");
        assertThrows(InvalidCommunicationEventException.class, () -> decoder.decode(mapper.writeValueAsString(value)));
        var self = payload(); self.put("recipientUserIds", List.of(self.get("actorUserId")));
        assertThrows(InvalidCommunicationEventException.class, () -> decoder.decode(mapper.writeValueAsString(self)));
        for (String input : new String[]{null, "", "null", "[]", "invalid", "a".repeat(16_385)})
            assertThrows(InvalidCommunicationEventException.class, () -> decoder.decode(input));
    }
}
