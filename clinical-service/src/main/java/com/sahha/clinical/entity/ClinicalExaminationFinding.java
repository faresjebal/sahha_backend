package com.sahha.clinical.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "clinical_examination_finding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalExaminationFinding {

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(nullable = false)
	private int position;
	@Column(name = "body_system", nullable = false, length = 160)
	private String bodySystem;
	@Column(nullable = false, length = 1000)
	private String finding;
	@Column(length = 1000)
	private String notes;

	public static ClinicalExaminationFinding create(
			UUID consultationId, int position, String bodySystem,
			String finding, String notes) {
		ClinicalExaminationFinding value = new ClinicalExaminationFinding();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		if (position < 0) throw new IllegalArgumentException("position is invalid");
		value.position = position;
		value.bodySystem = ClinicalText.required(bodySystem, 160, "bodySystem");
		value.finding = ClinicalText.required(finding, 1000, "finding");
		value.notes = ClinicalText.optional(notes, 1000, "examination.notes");
		return value;
	}
}
