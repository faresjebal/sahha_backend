package com.sahha.clinical.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sahha.clinical.entity.ClinicalHistoryCategory;
import com.sahha.clinical.entity.ConsultationStatus;
import com.sahha.clinical.entity.CorrectionTargetType;
import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.DiagnosisType;
import com.sahha.clinical.entity.MedicationKind;
import com.sahha.clinical.entity.SymptomSeverity;

public record ClinicalRecordResponse(
		UUID id,
		UUID organisationId,
		UUID appointmentId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		ConsultationStatus status,
		String reasonForConsultation,
		String clinicalAssessment,
		String treatmentPlan,
		String followUpInstructions,
		String additionalNotes,
		List<SymptomItem> symptoms,
		List<HistoryItem> history,
		VitalSignsItem vitalSigns,
		List<ExaminationItem> examinationFindings,
		List<DiagnosisItem> diagnoses,
		List<MedicationItem> medications,
		List<CorrectionItem> corrections,
		Instant finalizedAt,
		UUID finalizedByUserId,
		Instant createdAt,
		Instant updatedAt,
		long version) {

	public record SymptomItem(
			UUID id, String name, String onsetDescription,
			SymptomSeverity severity, String notes) {
	}

	public record HistoryItem(
			UUID id, ClinicalHistoryCategory category,
			String description, String notes) {
	}

	public record VitalSignsItem(
			UUID id, Instant measuredAt, BigDecimal temperatureCelsius,
			Integer systolicBloodPressure, Integer diastolicBloodPressure,
			Integer heartRateBpm, Integer respiratoryRateBpm,
			BigDecimal oxygenSaturationPercent, BigDecimal weightKg,
			BigDecimal heightCm, Map<String, Object> specialtyMeasurements) {
	}

	public record ExaminationItem(
			UUID id, String bodySystem, String finding, String notes) {
	}

	public record DiagnosisItem(
			UUID id, String code, String codeSystem, String label,
			DiagnosisType type, DiagnosisStatus status, String notes) {
	}

	public record MedicationItem(
			UUID id, MedicationKind kind, String name, String strength,
			String form, String dosage, String frequency, String route,
			String duration, String quantity, String specialInstructions) {
	}

	public record CorrectionItem(
			UUID id, CorrectionTargetType targetType, UUID targetId,
			String fieldName, String oldValue, String newValue,
			String reason, UUID actorUserId, long consultationVersion,
			Instant correctedAt) {
	}
}
