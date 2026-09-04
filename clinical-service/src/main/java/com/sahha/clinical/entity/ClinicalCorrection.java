package com.sahha.clinical.entity;

import java.time.Instant;
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
import lombok.ToString;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "clinical_correction")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class ClinicalCorrection {

	@Id @Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;
	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "actor_user_id", nullable = false, updatable = false)
	private UUID actorUserId;
	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, length = 32, updatable = false)
	private CorrectionTargetType targetType;
	@Column(name = "target_id", updatable = false)
	private UUID targetId;
	@Column(name = "field_name", nullable = false, length = 64, updatable = false)
	private String fieldName;
	@Column(name = "old_value", columnDefinition = "text", updatable = false)
	private String oldValue;
	@Column(name = "new_value", columnDefinition = "text", updatable = false)
	private String newValue;
	@Column(nullable = false, length = 1000, updatable = false)
	private String reason;
	@Column(name = "consultation_version", nullable = false, updatable = false)
	private long consultationVersion;
	@Column(name = "corrected_at", nullable = false, updatable = false)
	private Instant correctedAt;

	public static ClinicalCorrection create(
			Consultation consultation,
			UUID actorUserId,
			CorrectionTargetType targetType,
			UUID targetId,
			String fieldName,
			String oldValue,
			String newValue,
			String reason,
			long consultationVersion,
			Instant correctedAt) {
		ClinicalCorrection correction = new ClinicalCorrection();
		correction.id = UUID.randomUUID();
		correction.consultationId = consultation.getId();
		correction.organisationId = consultation.getOrganisationId();
		correction.actorUserId = Objects.requireNonNull(actorUserId);
		correction.targetType = Objects.requireNonNull(targetType);
		correction.targetId = targetId;
		if ((targetType == CorrectionTargetType.CONSULTATION) != (targetId == null)) {
			throw new IllegalArgumentException("correction target is invalid");
		}
		correction.fieldName = ClinicalText.required(fieldName, 64, "fieldName");
		correction.oldValue = normalizedValue(oldValue);
		correction.newValue = normalizedValue(newValue);
		if (Objects.equals(correction.oldValue, correction.newValue)) {
			throw new IllegalArgumentException("correction must change the value");
		}
		correction.reason = ClinicalText.required(reason, 1000, "reason");
		if (consultationVersion <= 0) {
			throw new IllegalArgumentException("consultationVersion is invalid");
		}
		correction.consultationVersion = consultationVersion;
		correction.correctedAt = Objects.requireNonNull(correctedAt);
		return correction;
	}

	private static String normalizedValue(String value) {
		if (value == null) return null;
		String stripped = value.strip();
		if (stripped.isEmpty()) return null;
		if (stripped.length() > 20_000) {
			throw new IllegalArgumentException("correction value is invalid");
		}
		return stripped;
	}
}
