package com.sahha.patient.mapper;

import java.util.List;

import org.springframework.stereotype.Component;

import com.sahha.patient.dto.response.PatientAdministrativeResponse;
import com.sahha.patient.dto.response.PatientAdministrativeSummaryResponse;
import com.sahha.patient.dto.response.PatientAuditHistoryResponse;
import com.sahha.patient.entity.PatientAuditEvent;
import com.sahha.patient.entity.PatientIdentity;
import com.sahha.patient.entity.PatientOrganisationRegistration;

@Component
public class PatientAdministrativeMapper {

	public PatientAdministrativeSummaryResponse toSummary(
			PatientOrganisationRegistration registration) {
		PatientIdentity patient = registration.getPatient();
		return new PatientAdministrativeSummaryResponse(
				registration.getId(),
				patient.getId(),
				registration.getMedicalRecordNumber(),
				patient.getFirstName(),
				patient.getLastName(),
				patient.getDateOfBirth(),
				patient.getSex(),
				registration.getPhoneNumber(),
				registration.getEmail(),
				registration.getStatus(),
				registration.getCreatedAt(),
				registration.getVersion(),
				patient.getVersion());
	}

	public PatientAdministrativeResponse toResponse(
			PatientOrganisationRegistration registration) {
		PatientIdentity patient = registration.getPatient();
		return new PatientAdministrativeResponse(
				registration.getId(),
				patient.getId(),
				registration.getOrganisationId(),
				registration.getMedicalRecordNumber(),
				registration.getStatus(),
				patient.getFirstName(),
				patient.getLastName(),
				patient.getDateOfBirth(),
				patient.getSex(),
				patient.getIdentifierType(),
				patient.getIdentifierLastFour() == null
						? null
						: "****" + patient.getIdentifierLastFour(),
				patient.getIdentifierCountryCode(),
				registration.getPhoneNumber(),
				registration.getEmail(),
				registration.getAddress(),
				registration.getCity(),
				registration.getRegion(),
				registration.getPostalCode(),
				registration.getCountryCode(),
				registration.getEmergencyContactName(),
				registration.getEmergencyContactPhone(),
				registration.getEmergencyContactRelationship(),
				registration.getPreferredLanguage(),
				registration.getAccessibilityNeeds(),
				registration.getPrivacyNoticeAcknowledgedAt(),
				registration.getCreatedAt(),
				registration.getRegisteredBy(),
				registration.getUpdatedBy(),
				registration.getCreatedAt(),
				registration.getUpdatedAt(),
				registration.getVersion(),
				patient.getVersion());
	}

	public PatientAuditHistoryResponse toHistory(PatientAuditEvent event) {
		Object rawFields = event.getMetadata().get("changedFields");
		List<String> changedFields = rawFields instanceof List<?> values
				? values.stream().filter(String.class::isInstance)
						.map(String.class::cast).toList()
				: List.of();
		return new PatientAuditHistoryResponse(
				event.getId(),
				event.getEventType(),
				event.getResult(),
				event.getActorUserId(),
				event.getResourceVersion(),
				changedFields,
				event.getRequestId(),
				event.getOccurredAt());
	}
}
