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
@Table(name = "clinical_history_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalHistoryEntry {

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(nullable = false)
	private int position;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private ClinicalHistoryCategory category;
	@Column(nullable = false, length = 1000)
	private String description;
	@Column(length = 1000)
	private String notes;

	public static ClinicalHistoryEntry create(
			UUID consultationId, int position, ClinicalHistoryCategory category,
			String description, String notes) {
		ClinicalHistoryEntry value = new ClinicalHistoryEntry();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		if (position < 0) throw new IllegalArgumentException("position is invalid");
		value.position = position;
		value.category = Objects.requireNonNull(category);
		value.description = ClinicalText.required(
				description, 1000, "history.description");
		value.notes = ClinicalText.optional(notes, 1000, "history.notes");
		return value;
	}
}
