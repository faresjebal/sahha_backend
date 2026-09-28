package com.sahha.notification.event;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import com.sahha.notification.service.messagenotificationservice.RejectedCommunicationEventService;
import com.sahha.notification.service.referralnotificationservice.ReferralNotificationService;

class ReferralEventConsumerTests {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ReferralEventDecoder decoder = new ReferralEventDecoder(mapper);
    private final ReferralNotificationService service = mock(ReferralNotificationService.class);
    private final RejectedCommunicationEventService rejected = mock(RejectedCommunicationEventService.class);
    private final ReferralEventConsumer consumer = new ReferralEventConsumer(decoder, service, rejected);
    @Test void deliversTheValidatedEventWithItsKafkaPosition() {
        var payload = mapper.writeValueAsString(ReferralEventDecoderTests.payload());
        var event = decoder.decode(payload);
        consumer.consume(new ConsumerRecord<>("referrals", 0, 1, event.referralId().toString(), payload));
        verify(service).consume(event, new CommunicationEventSource("referrals", 0, 1));
        verifyNoInteractions(rejected);
    }
    @Test void rejectsMismatchedKeyWithoutNotificationWrites() {
        var payload = mapper.writeValueAsString(ReferralEventDecoderTests.payload());
        consumer.consume(new ConsumerRecord<>("referrals", 0, 1, "wrong", payload));
        verify(rejected).record(new CommunicationEventSource("referrals", 0, 1), payload, "REFERRAL_KEY_MISMATCH");
        verifyNoInteractions(service);
    }
    @Test void rejectsPrivateOrMalformedBodiesWithoutNotificationWrites() {
        consumer.consume(new ConsumerRecord<>("referrals", 0, 1, null, "private malformed body"));
        verify(rejected).record(new CommunicationEventSource("referrals", 0, 1),
                "private malformed body", "MALFORMED_PAYLOAD");
        verifyNoInteractions(service);
    }
    @Test void persistenceFailurePropagatesForKafkaRetry() {
        var payload = mapper.writeValueAsString(ReferralEventDecoderTests.payload());
        var event = decoder.decode(payload);
        when(service.consume(any(), any())).thenThrow(new IllegalStateException("Synthetic unavailable database"));
        assertThrows(IllegalStateException.class, () -> consumer.consume(
                new ConsumerRecord<>("referrals", 0, 1, event.referralId().toString(), payload)));
        verifyNoInteractions(rejected);
    }
}
