package com.sahha.scheduling.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sahha.scheduling.service.appointmentservice.ClinicalAppointmentCompletionService;

@Component
@ConditionalOnProperty(
		prefix = "sahha.scheduling.clinical-consumer",
		name = "enabled",
		havingValue = "true")
public class ClinicalEventConsumer {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(ClinicalEventConsumer.class);
	private final ClinicalEventDecoder decoder;
	private final ClinicalAppointmentCompletionService completionService;

	public ClinicalEventConsumer(
			ClinicalEventDecoder decoder,
			ClinicalAppointmentCompletionService completionService) {
		this.decoder = decoder;
		this.completionService = completionService;
	}

	@KafkaListener(
			topics = "${sahha.scheduling.clinical-consumer.topic}",
			groupId = "${sahha.scheduling.clinical-consumer.group-id}")
	public void consume(ConsumerRecord<String, String> record) {
		ClinicalConsultationEventV1 event;
		try {
			event = decoder.decode(record.value());
			if (record.key() == null
					|| !event.consultationId().toString().equals(record.key())) {
				throw new InvalidClinicalEventException("CONSULTATION_KEY_MISMATCH");
			}
		}
		catch (InvalidClinicalEventException rejected) {
			LOGGER.warn(
					"Clinical event rejected topic={} partition={} offset={} reason={}",
					record.topic(), record.partition(), record.offset(),
					rejected.getReasonCode());
			return;
		}
		if (!ClinicalEventDecoder.FINALIZED.equals(event.eventType())) {
			return;
		}
		var result = completionService.process(
				event,
				ClinicalEventSource.kafka(
						record.topic(), record.partition(), record.offset()),
				null);
		LOGGER.debug(
				"Clinical finalization processed eventId={} appointmentId={} outcome={}",
				event.eventId(), event.appointmentId(), result.outcome());
	}
}
