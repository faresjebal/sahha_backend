package com.sahha.clinical.dto.request;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.sahha.clinical.entity.ClinicalHistoryCategory;
import com.sahha.clinical.entity.DiagnosisStatus;
import com.sahha.clinical.entity.DiagnosisType;
import com.sahha.clinical.entity.MedicationKind;
import com.sahha.clinical.entity.SymptomSeverity;

public record ReplaceConsultationDraftRequest(
		@NotNull @PositiveOrZero Long version,
		@Size(max = 1000) String reasonForConsultation,
		@Size(max = 20000) String clinicalAssessment,
		@Size(max = 20000) String treatmentPlan,
		@Size(max = 20000) String followUpInstructions,
		@Size(max = 20000) String additionalNotes,
		@NotNull @Size(max = 50) List<@Valid SymptomItem> symptoms,
		@NotNull @Size(max = 100) List<@Valid HistoryItem> history,
		@Valid VitalSignsItem vitalSigns,
		@NotNull @Size(max = 100) List<@Valid ExaminationItem> examinationFindings,
		@NotNull @Size(max = 50) List<@Valid DiagnosisItem> diagnoses,
		@NotNull @Size(max = 100) List<@Valid MedicationItem> medications) {

	public record SymptomItem(
			@NotBlank @Size(max = 200) String name,
			@Size(max = 300) String onsetDescription,
			SymptomSeverity severity,
			@Size(max = 1000) String notes) {
	}

	public record HistoryItem(
			@NotNull ClinicalHistoryCategory category,
			@NotBlank @Size(max = 1000) String description,
			@Size(max = 1000) String notes) {
	}

	public record VitalSignsItem(
			@NotNull Instant measuredAt,
			@DecimalMin("25.0") @DecimalMax("50.0") BigDecimal temperatureCelsius,
			@Min(40) @Max(300) Integer systolicBloodPressure,
			@Min(20) @Max(200) Integer diastolicBloodPressure,
			@Min(20) @Max(300) Integer heartRateBpm,
			@Min(4) @Max(100) Integer respiratoryRateBpm,
			@DecimalMin("0.0") @DecimalMax("100.0") BigDecimal oxygenSaturationPercent,
			@DecimalMin("0.10") @DecimalMax("700.0") BigDecimal weightKg,
			@DecimalMin("10.0") @DecimalMax("300.0") BigDecimal heightCm,
			@Size(max = 20) Map<@Size(max = 64) String, Object> specialtyMeasurements) {
	}

	public record ExaminationItem(
			@NotBlank @Size(max = 160) String bodySystem,
			@NotBlank @Size(max = 1000) String finding,
			@Size(max = 1000) String notes) {
	}

	public record DiagnosisItem(
			@Size(max = 64) String code,
			@Size(max = 64) String codeSystem,
			@NotBlank @Size(max = 300) String label,
			@NotNull DiagnosisType type,
			@NotNull DiagnosisStatus status,
			@Size(max = 1000) String notes) {
	}

	public record MedicationItem(
			@NotNull MedicationKind kind,
			@NotBlank @Size(max = 300) String name,
			@Size(max = 100) String strength,
			@Size(max = 100) String form,
			@Size(max = 160) String dosage,
			@Size(max = 160) String frequency,
			@Size(max = 100) String route,
			@Size(max = 160) String duration,
			@Size(max = 100) String quantity,
			@Size(max = 1000) String specialInstructions) {
	}
}
