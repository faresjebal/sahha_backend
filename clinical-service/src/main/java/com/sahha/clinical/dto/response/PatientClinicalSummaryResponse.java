package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.DiagnosisType;
import com.sahha.clinical.entity.MedicationKind;

public record PatientClinicalSummaryResponse(
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		CareRelationship careRelationship,
		List<EncounterSummary> encounters,
		Instant generatedAt) {

	public record CareRelationship(
			UUID appointmentId, String appointmentStatus) {
	}

	public record EncounterSummary(
			UUID consultationId,
			UUID appointmentId,
			UUID doctorUserId,
			Instant finalizedAt,
			String reasonForConsultation,
			List<DiagnosisSummary> diagnoses,
			List<MedicationSummary> medications,
			List<String> allergies) {
	}

	public record DiagnosisSummary(
			String code,
			String codeSystem,
			String label,
			DiagnosisType type,
			DiagnosisStatus status) {
	}

	public record MedicationSummary(
			MedicationKind kind,
			String name,
			String strength,
			String dosage,
			String frequency,
			String route,
			String duration) {
	}
}
