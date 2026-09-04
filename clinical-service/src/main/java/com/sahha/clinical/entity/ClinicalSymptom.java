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
@Table(name = "clinical_symptom")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalSymptom {

	@Id
	@Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(nullable = false)
	private int position;
	@Column(nullable = false, length = 200)
	private String name;
	@Column(name = "onset_description", length = 300)
	private String onsetDescription;
	@Enumerated(EnumType.STRING)
	@Column(length = 24)
	private SymptomSeverity severity;
	@Column(length = 1000)
	private String notes;

	public static ClinicalSymptom create(
			UUID consultationId, int position, String name,
			String onsetDescription, SymptomSeverity severity, String notes) {
		ClinicalSymptom value = new ClinicalSymptom();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		value.position = requirePosition(position);
		value.name = ClinicalText.required(name, 200, "symptom.name");
		value.onsetDescription = ClinicalText.optional(
				onsetDescription, 300, "symptom.onsetDescription");
		value.severity = severity;
		value.notes = ClinicalText.optional(notes, 1000, "symptom.notes");
		return value;
	}

	private static int requirePosition(int position) {
		if (position < 0) throw new IllegalArgumentException("position is invalid");
		return position;
	}
}
