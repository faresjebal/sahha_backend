package com.sahha.notification.event;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.sahha.notification.service.appointmentnotificationservice.AppointmentEventProcessingResult;
import com.sahha.notification.service.appointmentnotificationservice.AppointmentNotificationService;
import com.sahha.notification.service.appointmentnotificationservice.RejectedAppointmentEventService;

@Component
@ConditionalOnProperty(
		prefix = "sahha.notification.appointment-consumer",
		name = "enabled",
		havingValue = "true")
public class AppointmentEventConsumer {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(AppointmentEventConsumer.class);

	private final AppointmentEventDecoder decoder;
	private final AppointmentNotificationService notificationService;
	private final RejectedAppointmentEventService rejectedEventService;

	public AppointmentEventConsumer(
			AppointmentEventDecoder decoder,
			AppointmentNotificationService notificationService,
			RejectedAppointmentEventService rejectedEventService) {
		this.decoder = decoder;
		this.notificationService = notificationService;
		this.rejectedEventService = rejectedEventService;
	}

	@KafkaListener(
			topics = "${sahha.notification.appointment-consumer.topic}",
			groupId = "${sahha.notification.appointment-consumer.group-id}")
	public void consume(ConsumerRecord<String, String> record) {
		AppointmentEventSource source = new AppointmentEventSource(
				record.topic(), record.partition(), record.offset());
		AppointmentEventV1 event;
		try {
			event = decoder.decode(record.value());
			if (record.key() == null
					|| !event.appointmentId().toString().equals(record.key())) {
				throw new InvalidAppointmentEventException(
						"APPOINTMENT_KEY_MISMATCH");
			}
		}
		catch (InvalidAppointmentEventException rejected) {
			rejectedEventService.record(
					source, record.value(), rejected.getReasonCode());
			LOGGER.warn(
					"Appointment event rejected topic={} partition={} offset={} reason={}",
					source.topic(), source.partition(), source.offset(),
					rejected.getReasonCode());
			return;
		}
		AppointmentEventProcessingResult result = notificationService.consume(
				event, source);
		LOGGER.debug(
				"Appointment event processed eventId={} appointmentId={} result={}",
				event.eventId(), event.appointmentId(), result);
	}
}
