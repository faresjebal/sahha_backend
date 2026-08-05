package com.sahha.patient.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "patient_organisation_registration")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class PatientOrganisationRegistration {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "patient_id", nullable = false, updatable = false)
	private PatientIdentity patient;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "medical_record_number", nullable = false, length = 32,
			updatable = false)
	@ToString.Include
	private String medicalRecordNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private PatientRegistrationStatus status;

	@Column(name = "phone_number", nullable = false, length = 32)
	private String phoneNumber;

	@Column(name = "normalized_phone_number", nullable = false, length = 32)
	private String normalizedPhoneNumber;

	@Column(length = 254)
	private String email;

	@Column(name = "normalized_email", length = 254)
	private String normalizedEmail;

	@Column(nullable = false, length = 300)
	private String address;

	@Column(length = 100)
	private String city;

	@Column(length = 100)
	private String region;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode;

	@Column(name = "emergency_contact_name", length = 160)
	private String emergencyContactName;

	@Column(name = "emergency_contact_phone", length = 32)
	private String emergencyContactPhone;

	@Column(name = "emergency_contact_relationship", length = 80)
	private String emergencyContactRelationship;

	@Column(name = "preferred_language", length = 64)
	private String preferredLanguage;

	@Column(name = "accessibility_needs", length = 500)
	private String accessibilityNeeds;

	@Column(name = "privacy_notice_acknowledged_at", nullable = false,
			updatable = false)
	private Instant privacyNoticeAcknowledgedAt;

	@Column(name = "registered_by", nullable = false, updatable = false)
	private UUID registeredBy;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static PatientOrganisationRegistration create(
			PatientIdentity patient,
			UUID organisationId,
			String phoneNumber,
			String normalizedPhoneNumber,
			String email,
			String normalizedEmail,
			String address,
			String city,
			String region,
			String postalCode,
			String countryCode,
			String emergencyContactName,
			String emergencyContactPhone,
			String emergencyContactRelationship,
			String preferredLanguage,
			String accessibilityNeeds,
			UUID actorUserId,
			Instant createdAt) {
		PatientOrganisationRegistration registration =
				new PatientOrganisationRegistration();
		registration.id = UUID.randomUUID();
		registration.patient = Objects.requireNonNull(
				patient,
				"patient must not be null");
		registration.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		registration.medicalRecordNumber = "PT-"
				+ UUID.randomUUID().toString().replace("-", "")
						.substring(0, 12).toUpperCase(Locale.ROOT);
		registration.status = PatientRegistrationStatus.ACTIVE;
		registration.setAdministrativeDetails(
				phoneNumber,
				normalizedPhoneNumber,
				email,
				normalizedEmail,
				address,
				city,
				region,
				postalCode,
				countryCode,
				emergencyContactName,
				emergencyContactPhone,
				emergencyContactRelationship,
				preferredLanguage,
				accessibilityNeeds);
		registration.privacyNoticeAcknowledgedAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		registration.registeredBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		registration.updatedBy = actorUserId;
		registration.createdAt = createdAt;
		registration.updatedAt = createdAt;
		return registration;
	}

	public void updateAdministrativeDetails(
			String phoneNumber,
			String normalizedPhoneNumber,
			String email,
			String normalizedEmail,
			String address,
			String city,
			String region,
			String postalCode,
			String countryCode,
			String emergencyContactName,
			String emergencyContactPhone,
			String emergencyContactRelationship,
			String preferredLanguage,
			String accessibilityNeeds,
			UUID actorUserId,
			Instant updatedAt) {
		setAdministrativeDetails(
				phoneNumber,
				normalizedPhoneNumber,
				email,
				normalizedEmail,
				address,
				city,
				region,
				postalCode,
				countryCode,
				emergencyContactName,
				emergencyContactPhone,
				emergencyContactRelationship,
				preferredLanguage,
				accessibilityNeeds);
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

	private void setAdministrativeDetails(
			String phoneNumber,
			String normalizedPhoneNumber,
			String email,
			String normalizedEmail,
			String address,
			String city,
			String region,
			String postalCode,
			String countryCode,
			String emergencyContactName,
			String emergencyContactPhone,
			String emergencyContactRelationship,
			String preferredLanguage,
			String accessibilityNeeds) {
		this.phoneNumber = required(phoneNumber, 32, "phoneNumber");
		this.normalizedPhoneNumber = required(
				normalizedPhoneNumber,
				32,
				"normalizedPhoneNumber");
		this.email = optional(email, 254, "email");
		this.normalizedEmail = optional(normalizedEmail, 254, "normalizedEmail");
		if ((this.email == null) != (this.normalizedEmail == null)) {
			throw new IllegalArgumentException("email fields must be paired");
		}
		this.address = required(address, 300, "address");
		this.city = optional(city, 100, "city");
		this.region = optional(region, 100, "region");
		this.postalCode = optional(postalCode, 20, "postalCode");
		this.countryCode = required(countryCode, 2, "countryCode")
				.toUpperCase(Locale.ROOT);
		this.emergencyContactName = optional(
				emergencyContactName,
				160,
				"emergencyContactName");
		this.emergencyContactPhone = optional(
				emergencyContactPhone,
				32,
				"emergencyContactPhone");
		this.emergencyContactRelationship = optional(
				emergencyContactRelationship,
				80,
				"emergencyContactRelationship");
		if ((this.emergencyContactName == null)
				!= (this.emergencyContactPhone == null)) {
			throw new IllegalArgumentException(
					"emergency contact name and phone must be paired");
		}
		if (this.emergencyContactName == null) {
			this.emergencyContactRelationship = null;
		}
		this.preferredLanguage = optional(
				preferredLanguage,
				64,
				"preferredLanguage");
		this.accessibilityNeeds = optional(
				accessibilityNeeds,
				500,
				"accessibilityNeeds");
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

	private static String optional(
			String value,
			int maximumLength,
			String fieldName) {
		return value == null || value.isBlank()
				? null
				: required(value, maximumLength, fieldName);
	}
}
