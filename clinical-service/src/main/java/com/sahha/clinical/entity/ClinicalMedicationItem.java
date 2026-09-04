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
@Table(name = "clinical_medication_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClinicalMedicationItem {

	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(nullable = false)
	private int position;
	@Enumerated(EnumType.STRING)
	@Column(name = "medication_kind", nullable = false, length = 24)
	private MedicationKind kind;
	@Column(name = "medication_name", nullable = false, length = 300)
	private String name;
	@Column(length = 100)
	private String strength;
	@Column(name = "medication_form", length = 100)
	private String form;
	@Column(length = 160)
	private String dosage;
	@Column(length = 160)
	private String frequency;
	@Column(length = 100)
	private String route;
	@Column(length = 160)
	private String duration;
	@Column(length = 100)
	private String quantity;
	@Column(name = "special_instructions", length = 1000)
	private String specialInstructions;

	public static ClinicalMedicationItem create(
			UUID consultationId, int position, MedicationKind kind, String name,
			String strength, String form, String dosage, String frequency,
			String route, String duration, String quantity,
			String specialInstructions) {
		ClinicalMedicationItem value = new ClinicalMedicationItem();
		value.id = UUID.randomUUID();
		value.consultationId = Objects.requireNonNull(consultationId);
		if (position < 0) throw new IllegalArgumentException("position is invalid");
		value.position = position;
		value.kind = Objects.requireNonNull(kind);
		value.name = ClinicalText.required(name, 300, "medication.name");
		value.strength = ClinicalText.optional(strength, 100, "medication.strength");
		value.form = ClinicalText.optional(form, 100, "medication.form");
		value.dosage = ClinicalText.optional(dosage, 160, "medication.dosage");
		value.frequency = ClinicalText.optional(
				frequency, 160, "medication.frequency");
		value.route = ClinicalText.optional(route, 100, "medication.route");
		value.duration = ClinicalText.optional(duration, 160, "medication.duration");
		value.quantity = ClinicalText.optional(quantity, 100, "medication.quantity");
		value.specialInstructions = ClinicalText.optional(
				specialInstructions, 1000, "medication.specialInstructions");
		return value;
	}
}
