package com.sahha.notification.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import com.sahha.notification.service.messagenotificationservice.RejectedCommunicationEventService;
import com.sahha.notification.service.referralnotificationservice.ReferralNotificationService;

@Component
@ConditionalOnProperty(prefix = "sahha.notification.referral-consumer", name = "enabled", havingValue = "true")
public class ReferralEventConsumer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReferralEventConsumer.class);
    private final ReferralEventDecoder decoder;
    private final ReferralNotificationService service;
    private final RejectedCommunicationEventService rejected;
    public ReferralEventConsumer(ReferralEventDecoder decoder, ReferralNotificationService service,
            RejectedCommunicationEventService rejected) {
        this.decoder = decoder; this.service = service; this.rejected = rejected;
    }
    @KafkaListener(topics = "${sahha.notification.referral-consumer.topic}",
            groupId = "${sahha.notification.referral-consumer.group-id}")
    public void consume(ConsumerRecord<String, String> record) {
        var source = new CommunicationEventSource(record.topic(), record.partition(), record.offset());
        ReferralEventV1 event;
        try {
            event = decoder.decode(record.value());
            if (!event.referralId().toString().equals(record.key()))
                throw new InvalidCommunicationEventException("REFERRAL_KEY_MISMATCH");
        } catch (InvalidCommunicationEventException invalid) {
            rejected.record(source, record.value(), invalid.getReasonCode());
            LOGGER.warn("Referral event rejected topic={} partition={} offset={} reason={}",
                    source.topic(), source.partition(), source.offset(), invalid.getReasonCode());
            return;
        }
        // Persistence failures propagate for Kafka retry. WebSocket publishes only after commit.
        service.consume(event, source);
    }
}
