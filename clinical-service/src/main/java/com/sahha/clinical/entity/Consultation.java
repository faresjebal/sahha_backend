package com.sahha.clinical.entity;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "clinical_consultation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Consultation {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "appointment_id", nullable = false, updatable = false)
	private UUID appointmentId;

	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "doctor_user_id", nullable = false, updatable = false)
	private UUID doctorUserId;

	@Column(name = "doctor_membership_id", nullable = false, updatable = false)
	private UUID doctorMembershipId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private ConsultationStatus status;

	@Column(name = "reason_for_consultation", length = 1000)
	private String reasonForConsultation;

	@Column(name = "draft_notes", columnDefinition = "text")
	private String draftNotes;

	@Column(name = "clinical_assessment", columnDefinition = "text")
	private String clinicalAssessment;

	@Column(name = "treatment_plan", columnDefinition = "text")
	private String treatmentPlan;

	@Column(name = "follow_up_instructions", columnDefinition = "text")
	private String followUpInstructions;

	@Column(name = "finalized_at")
	private Instant finalizedAt;

	@Column(name = "finalized_by_user_id")
	private UUID finalizedByUserId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static Consultation draft(
			UUID organisationId,
			UUID appointmentId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID doctorUserId,
			UUID doctorMembershipId,
			Clock clock) {
		Consultation consultation = new Consultation();
		consultation.id = UUID.randomUUID();
		consultation.organisationId = Objects.requireNonNull(organisationId);
		consultation.appointmentId = Objects.requireNonNull(appointmentId);
		consultation.patientRegistrationId = Objects.requireNonNull(
				patientRegistrationId);
		consultation.patientId = Objects.requireNonNull(patientId);
		consultation.doctorUserId = Objects.requireNonNull(doctorUserId);
		consultation.doctorMembershipId = Objects.requireNonNull(
				doctorMembershipId);
		consultation.status = ConsultationStatus.DRAFT;
		consultation.createdAt = Instant.now(Objects.requireNonNull(clock));
		consultation.updatedAt = consultation.createdAt;
		return consultation;
	}

	public void updateDraft(
			String reasonForConsultation,
			String draftNotes,
			Clock clock) {
		requireDraft();
		this.reasonForConsultation = optional(
				reasonForConsultation, 1000, "reasonForConsultation");
		this.draftNotes = optional(draftNotes, 20_000, "draftNotes");
		updatedAt = Instant.now(Objects.requireNonNull(clock));
	}

	public void replaceNarrativeDraft(
			String reasonForConsultation,
			String clinicalAssessment,
			String treatmentPlan,
			String followUpInstructions,
			String additionalNotes,
			Clock clock) {
		requireDraft();
		this.reasonForConsultation = optional(
				reasonForConsultation, 1000, "reasonForConsultation");
		this.clinicalAssessment = optional(
				clinicalAssessment, 20_000, "clinicalAssessment");
		this.treatmentPlan = optional(treatmentPlan, 20_000, "treatmentPlan");
		this.followUpInstructions = optional(
				followUpInstructions, 20_000, "followUpInstructions");
		this.draftNotes = optional(additionalNotes, 20_000, "additionalNotes");
		updatedAt = Instant.now(Objects.requireNonNull(clock));
	}

	public void finalizeRecord(UUID actorUserId, Clock clock) {
		requireDraft();
		Instant now = Instant.now(Objects.requireNonNull(clock));
		status = ConsultationStatus.FINALIZED;
		finalizedAt = now;
		finalizedByUserId = Objects.requireNonNull(actorUserId);
		updatedAt = now;
	}

	public void recordCorrection(Clock clock) {
		if (status != ConsultationStatus.FINALIZED) {
			throw new IllegalStateException("only a finalized consultation can be corrected");
		}
		updatedAt = Instant.now(Objects.requireNonNull(clock));
	}

	private void requireDraft() {
		if (status != ConsultationStatus.DRAFT) {
			throw new IllegalStateException("only a draft consultation can change");
		}
	}

	private static String optional(
			String value,
			int maximumLength,
			String fieldName) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String stripped = value.strip();
		if (stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
