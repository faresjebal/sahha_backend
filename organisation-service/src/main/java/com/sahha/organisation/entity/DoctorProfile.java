package com.sahha.organisation.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "doctor_profile")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class DoctorProfile {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "membership_id", nullable = false, updatable = false)
	private UUID membershipId;

	@Column(nullable = false, length = 120)
	private String specialty;

	@Column(name = "professional_title", nullable = false, length = 120)
	private String professionalTitle;

	@Column(name = "licence_number", nullable = false, length = 80)
	private String licenceNumber;

	@Column(name = "normalized_licence_number", nullable = false, length = 80)
	private String normalizedLicenceNumber;

	@Column(name = "registration_authority", nullable = false, length = 160)
	private String registrationAuthority;

	@Column(length = 1000)
	private String biography;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static DoctorProfile create(
			UUID organisationId,
			UUID membershipId,
			String specialty,
			String professionalTitle,
			String licenceNumber,
			String registrationAuthority,
			String biography,
			UUID actorUserId,
			Instant createdAt) {
		DoctorProfile profile = new DoctorProfile();
		profile.id = UUID.randomUUID();
		profile.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		profile.membershipId = Objects.requireNonNull(
				membershipId,
				"membershipId must not be null");
		profile.setDetails(
				specialty,
				professionalTitle,
				licenceNumber,
				registrationAuthority,
				biography);
		profile.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		profile.updatedBy = actorUserId;
		profile.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		profile.updatedAt = createdAt;
		return profile;
	}

	public void update(
			String specialty,
			String professionalTitle,
			String licenceNumber,
			String registrationAuthority,
			String biography,
			UUID actorUserId,
			Instant updatedAt) {
		setDetails(
				specialty,
				professionalTitle,
				licenceNumber,
				registrationAuthority,
				biography);
		this.updatedBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		Instant requiredUpdatedAt = Objects.requireNonNull(
				updatedAt,
				"updatedAt must not be null");
		if (requiredUpdatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					"updatedAt must not precede createdAt");
		}
		this.updatedAt = requiredUpdatedAt;
	}

	private void setDetails(
			String specialty,
			String professionalTitle,
			String licenceNumber,
			String registrationAuthority,
			String biography) {
		this.specialty = required(specialty, 120, "specialty");
		this.professionalTitle = required(
				professionalTitle,
				120,
				"professionalTitle");
		this.licenceNumber = required(
				licenceNumber,
				80,
				"licenceNumber");
		this.normalizedLicenceNumber = this.licenceNumber
				.toLowerCase(Locale.ROOT);
		this.registrationAuthority = required(
				registrationAuthority,
				160,
				"registrationAuthority");
		this.biography = optional(biography, 1000, "biography");
	}

	private static String required(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String normalized = value.strip().replaceAll("\\s+", " ");
		if (normalized.isEmpty() || normalized.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return normalized;
	}

	private static String optional(
			String value,
			int maximumLength,
			String fieldName) {
		return value == null || value.isBlank()
				? null
				: required(value, maximumLength, fieldName);
	}
}
