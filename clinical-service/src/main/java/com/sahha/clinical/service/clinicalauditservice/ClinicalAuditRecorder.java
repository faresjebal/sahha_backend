package com.sahha.clinical.service.clinicalauditservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.clinical.entity.ClinicalAuditEvent;
import com.sahha.clinical.entity.ClinicalAuditEventType;
import com.sahha.clinical.entity.ClinicalOutboxEvent;
import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.event.ClinicalDomainEventType;
import com.sahha.clinical.event.ClinicalEventMapper;
import com.sahha.clinical.repository.ClinicalAuditEventRepository;
import com.sahha.clinical.repository.ClinicalOutboxEventRepository;

@Service
public class ClinicalAuditRecorder {

	private final ClinicalAuditEventRepository auditRepository;
	private final ClinicalOutboxEventRepository outboxRepository;
	private final ClinicalEventMapper eventMapper;
	private final Clock clock;

	public ClinicalAuditRecorder(
			ClinicalAuditEventRepository auditRepository,
			ClinicalOutboxEventRepository outboxRepository,
			ClinicalEventMapper eventMapper,
			Clock clock) {
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	public void recordCommand(
			Consultation consultation,
			UUID actorUserId,
			ClinicalAuditEventType eventType,
			String requestId) {
		Instant occurredAt = Instant.now(clock);
		ClinicalAuditEvent audit = auditRepository.saveAndFlush(
				ClinicalAuditEvent.success(
						consultation, actorUserId, eventType,
						consultation.getVersion(), requestId, occurredAt));
		UUID eventId = UUID.randomUUID();
		ClinicalDomainEventType domainEventType =
				ClinicalDomainEventType.fromAudit(eventType);
		outboxRepository.save(ClinicalOutboxEvent.pending(
				eventId,
				audit,
				consultation,
				domainEventType.wireName(),
				eventMapper.toPayload(
						eventId, domainEventType, consultation, audit)));
	}
}
