package com.sahha.clinical.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "clinical_diagnosis")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalDiagnosis {

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(nullable = false)
	private int position;
	@Column(length = 64)
	private String code;
	@Column(name = "code_system", length = 64)
	private String codeSystem;
	@Column(nullable = false, length = 300)
	private String label;
	@Enumerated(EnumType.STRING)
	@Column(name = "diagnosis_type", nullable = false, length = 24)
	private DiagnosisType type;
	@Enumerated(EnumType.STRING)
	@Column(name = "diagnosis_status", nullable = false, length = 24)
	private DiagnosisStatus status;
	@Column(length = 1000)
	private String notes;

	public static ClinicalDiagnosis create(
			UUID consultationId, int position, String code, String codeSystem,
			String label, DiagnosisType type, DiagnosisStatus status, String notes) {
		ClinicalDiagnosis value = new ClinicalDiagnosis();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		if (position < 0) throw new IllegalArgumentException("position is invalid");
		value.position = position;
		value.code = ClinicalText.optional(code, 64, "diagnosis.code");
		value.codeSystem = ClinicalText.optional(
				codeSystem, 64, "diagnosis.codeSystem");
		if ((value.code == null) != (value.codeSystem == null)) {
			throw new IllegalArgumentException("diagnosis code and system are paired");
		}
		value.label = ClinicalText.required(label, 300, "diagnosis.label");
		value.type = Objects.requireNonNull(type);
		value.status = Objects.requireNonNull(status);
		value.notes = ClinicalText.optional(notes, 1000, "diagnosis.notes");
		return value;
	}
}
