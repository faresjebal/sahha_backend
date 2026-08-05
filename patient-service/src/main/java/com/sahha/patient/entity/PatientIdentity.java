package com.sahha.patient.entity;

import java.time.Instant;
import java.time.LocalDate;
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
@Table(name = "patient_identity")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class PatientIdentity {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "first_name", nullable = false, length = 80)
	private String firstName;

	@Column(name = "normalized_first_name", nullable = false, length = 80)
	private String normalizedFirstName;

	@Column(name = "last_name", nullable = false, length = 80)
	private String lastName;

	@Column(name = "normalized_last_name", nullable = false, length = 80)
	private String normalizedLastName;

	@Column(name = "date_of_birth", nullable = false)
	private LocalDate dateOfBirth;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private PatientSex sex;

	@Enumerated(EnumType.STRING)
	@Column(name = "identifier_type", length = 24)
	private PatientIdentifierType identifierType;

	@Column(name = "identifier_fingerprint", length = 64)
	private String identifierFingerprint;

	@Column(name = "identifier_last_four", length = 4)
	private String identifierLastFour;

	@Column(name = "identifier_country_code", length = 2)
	private String identifierCountryCode;

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

	public static PatientIdentity create(
			String firstName,
			String normalizedFirstName,
			String lastName,
			String normalizedLastName,
			LocalDate dateOfBirth,
			PatientSex sex,
			PatientIdentifierType identifierType,
			String identifierFingerprint,
			String identifierLastFour,
			String identifierCountryCode,
			UUID actorUserId,
			Instant createdAt) {
		PatientIdentity patient = new PatientIdentity();
		patient.id = UUID.randomUUID();
		patient.setIdentity(
				firstName,
				normalizedFirstName,
				lastName,
				normalizedLastName,
				dateOfBirth,
				sex,
				identifierType,
				identifierFingerprint,
				identifierLastFour,
				identifierCountryCode);
		patient.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		patient.updatedBy = actorUserId;
		patient.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		patient.updatedAt = createdAt;
		return patient;
	}

	public void updateIdentity(
			String firstName,
			String normalizedFirstName,
			String lastName,
			String normalizedLastName,
			LocalDate dateOfBirth,
			PatientSex sex,
			PatientIdentifierType identifierType,
			String identifierFingerprint,
			String identifierLastFour,
			String identifierCountryCode,
			UUID actorUserId,
			Instant updatedAt) {
		setIdentity(
				firstName,
				normalizedFirstName,
				lastName,
				normalizedLastName,
				dateOfBirth,
				sex,
				identifierType,
				identifierFingerprint,
				identifierLastFour,
				identifierCountryCode);
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

	private void setIdentity(
			String firstName,
			String normalizedFirstName,
			String lastName,
			String normalizedLastName,
			LocalDate dateOfBirth,
			PatientSex sex,
			PatientIdentifierType identifierType,
			String identifierFingerprint,
			String identifierLastFour,
			String identifierCountryCode) {
		this.firstName = required(firstName, 80, "firstName");
		this.normalizedFirstName = required(
				normalizedFirstName,
				80,
				"normalizedFirstName");
		this.lastName = required(lastName, 80, "lastName");
		this.normalizedLastName = required(
				normalizedLastName,
				80,
				"normalizedLastName");
		this.dateOfBirth = Objects.requireNonNull(
				dateOfBirth,
				"dateOfBirth must not be null");
		this.sex = Objects.requireNonNull(sex, "sex must not be null");
		if (identifierType == null) {
			if (identifierFingerprint != null
					|| identifierLastFour != null
					|| identifierCountryCode != null) {
				throw new IllegalArgumentException(
						"identifier fields must be complete");
			}
			this.identifierType = null;
			this.identifierFingerprint = null;
			this.identifierLastFour = null;
			this.identifierCountryCode = null;
			return;
		}
		this.identifierType = identifierType;
		this.identifierFingerprint = required(
				identifierFingerprint,
				64,
				"identifierFingerprint");
		this.identifierLastFour = required(
				identifierLastFour,
				4,
				"identifierLastFour");
		this.identifierCountryCode = required(
				identifierCountryCode,
				2,
				"identifierCountryCode");
	}

	private static String required(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String stripped = value.strip().replaceAll("\\s+", " ");
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
