package com.sahha.clinical.service.clinicalauditservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.clinical.entity.ClinicalAccessAuditEvent;
import com.sahha.clinical.repository.ClinicalAccessAuditEventRepository;

@Service
public class ClinicalAccessAuditService {

	private final ClinicalAccessAuditEventRepository repository;
	private final Clock clock;

	public ClinicalAccessAuditService(
			ClinicalAccessAuditEventRepository repository,
			Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(
			UUID organisationId,
			UUID actorUserId,
			String resourceType,
			UUID resourceId,
			UUID patientRegistrationId,
			UUID careAppointmentId,
			String result,
			String accessReason,
			String requestId) {
		repository.save(ClinicalAccessAuditEvent.record(
				organisationId, actorUserId, resourceType, resourceId,
				patientRegistrationId, careAppointmentId, result, accessReason,
				requestId, Instant.now(clock)));
	}
}
