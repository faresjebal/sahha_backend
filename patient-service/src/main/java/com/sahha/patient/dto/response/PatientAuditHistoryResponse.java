package com.sahha.patient.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sahha.patient.entity.PatientAuditEventType;
import com.sahha.patient.entity.PatientAuditResult;

public record PatientAuditHistoryResponse(
		UUID eventId,
		PatientAuditEventType eventType,
		PatientAuditResult result,
		UUID actorUserId,
		Long resourceVersion,
		List<String> changedFields,
		String requestId,
		Instant occurredAt) {
}
