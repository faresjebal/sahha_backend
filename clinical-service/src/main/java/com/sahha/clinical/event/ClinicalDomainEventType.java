package com.sahha.clinical.event;

import com.sahha.clinical.entity.ClinicalAuditEventType;

public enum ClinicalDomainEventType {
	CONSULTATION_DRAFT_CREATED("consultation.draft-created.v1"),
	CONSULTATION_DRAFT_UPDATED("consultation.draft-updated.v1"),
	CONSULTATION_FINALIZED("consultation.finalised.v1"),
	CONSULTATION_CORRECTED("consultation.corrected.v1");

	private final String wireName;

	ClinicalDomainEventType(String wireName) {
		this.wireName = wireName;
	}

	public String wireName() {
		return wireName;
	}

	public static ClinicalDomainEventType fromAudit(ClinicalAuditEventType type) {
		return valueOf(type.name());
	}
}
