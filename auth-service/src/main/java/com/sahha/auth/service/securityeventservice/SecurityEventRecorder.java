package com.sahha.auth.service.securityeventservice;

import java.time.Instant;
import java.util.Objects;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.AuthOutboxEvent;
import com.sahha.auth.entity.SecurityEvent;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.event.SecurityEventMapper;
import com.sahha.auth.repository.AuthOutboxEventRepository;
import com.sahha.auth.repository.SecurityEventRepository;
import com.sahha.auth.service.useraccountservice.EmailNormalizer;

@Service
public class SecurityEventRecorder {

	private final SecurityEventRepository eventRepository;
	private final AuthOutboxEventRepository outboxRepository;
	private final SecurityEventMapper eventMapper;
	private final EmailNormalizer emailNormalizer;

	public SecurityEventRecorder(
			SecurityEventRepository eventRepository,
			AuthOutboxEventRepository outboxRepository,
			SecurityEventMapper eventMapper,
			EmailNormalizer emailNormalizer) {
		this.eventRepository = eventRepository;
		this.outboxRepository = outboxRepository;
		this.eventMapper = eventMapper;
		this.emailNormalizer = emailNormalizer;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public SecurityEvent record(
			SecurityEventType eventType,
			SecurityEventResult result,
			String reasonCode,
			SecurityEventContext context,
			Instant occurredAt) {
		SecurityEventContext requiredContext = Objects.requireNonNull(
				context,
				"context must not be null");
		SecurityEvent event = SecurityEvent.create(
				requiredContext.userId(),
				requiredContext.subjectUserId(),
				normalizedEmail(requiredContext.attemptedEmail()),
				eventType,
				result,
				reasonCode,
				requiredContext.sessionId(),
				requiredContext.activeOrganisationId(),
				MDC.get("requestId"),
				requiredContext.ipAddress(),
				requiredContext.userAgent(),
				occurredAt);
		eventRepository.saveAndFlush(event);
		outboxRepository.save(AuthOutboxEvent.pending(
				event,
				eventMapper.toPayload(event)));
		return event;
	}

	private String normalizedEmail(String attemptedEmail) {
		if (attemptedEmail == null) {
			return null;
		}
		try {
			return emailNormalizer.normalize(attemptedEmail);
		}
		catch (RuntimeException invalidEmail) {
			return null;
		}
	}
}
