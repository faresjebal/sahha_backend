package com.sahha.scheduling.outbox;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentAuditEvent;
import com.sahha.scheduling.entity.AppointmentOutboxEvent;
import com.sahha.scheduling.event.AppointmentDomainEventType;
import com.sahha.scheduling.event.AppointmentEventMapper;
import com.sahha.scheduling.repository.AppointmentOutboxEventRepository;

@Component
public class AppointmentOutboxRecorder {

	private final AppointmentOutboxEventRepository repository;
	private final AppointmentEventMapper eventMapper;

	public AppointmentOutboxRecorder(
			AppointmentOutboxEventRepository repository,
			AppointmentEventMapper eventMapper) {
		this.repository = repository;
		this.eventMapper = eventMapper;
	}

	public AppointmentOutboxEvent record(
			Appointment appointment,
			AppointmentAuditEvent auditEvent) {
		UUID eventId = UUID.randomUUID();
		AppointmentDomainEventType eventType =
				AppointmentDomainEventType.fromAudit(auditEvent.getEventType());
		Map<String, Object> payload = eventMapper.toPayload(
				eventId, eventType, appointment, auditEvent);
		return repository.save(AppointmentOutboxEvent.pending(
				eventId, auditEvent, appointment, eventType, payload));
	}
}
