package com.sahha.notification.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sahha.notification.service.messagenotificationservice.MessageNotificationService;
import com.sahha.notification.service.messagenotificationservice.RejectedCommunicationEventService;

@Component
@ConditionalOnProperty(prefix = "sahha.notification.communication-consumer",
		name = "enabled", havingValue = "true")
public class CommunicationEventConsumer {
	private static final Logger LOGGER =
			LoggerFactory.getLogger(CommunicationEventConsumer.class);
	private final CommunicationEventDecoder decoder;
	private final MessageNotificationService notificationService;
	private final RejectedCommunicationEventService rejectedEventService;

	public CommunicationEventConsumer(CommunicationEventDecoder decoder,
			MessageNotificationService notificationService,
			RejectedCommunicationEventService rejectedEventService) {
		this.decoder = decoder;
		this.notificationService = notificationService;
		this.rejectedEventService = rejectedEventService;
	}

	@KafkaListener(
			topics = "${sahha.notification.communication-consumer.topic}",
			groupId = "${sahha.notification.communication-consumer.group-id}")
	public void consume(ConsumerRecord<String, String> record) {
		CommunicationEventSource source = new CommunicationEventSource(
				record.topic(), record.partition(), record.offset());
		CommunicationEventV1 event;
		try {
			var decoded = decoder.decode(record.value());
			if (decoded.isEmpty()) {
				LOGGER.debug("Communication event ignored topic={} partition={} offset={}",
						source.topic(), source.partition(), source.offset());
				return;
			}
			event = decoded.orElseThrow();
			if (record.key() == null
					|| !event.conversationId().toString().equals(record.key())) {
				throw new InvalidCommunicationEventException("CONVERSATION_KEY_MISMATCH");
			}
		}
		catch (InvalidCommunicationEventException rejected) {
			rejectedEventService.record(source, record.value(), rejected.getReasonCode());
			LOGGER.warn("Communication event rejected topic={} partition={} offset={} reason={}",
					source.topic(), source.partition(), source.offset(), rejected.getReasonCode());
			return;
		}
		var result = notificationService.consume(event, source);
		LOGGER.debug("Communication event processed eventId={} conversationId={} result={}",
				event.eventId(), event.conversationId(), result);
	}
}
