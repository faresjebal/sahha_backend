package com.sahha.organisation.entity;

import java.time.Instant;
import java.util.Locale;
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
@Table(name = "organisation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Organisation {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 160)
	private String normalizedName;

	@Column(name = "legal_name", length = 200)
	private String legalName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@ToString.Include
	private OrganisationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private OrganisationStatus status;

	@Column(name = "contact_email", nullable = false, length = 254)
	private String contactEmail;

	@Column(name = "phone_number", nullable = false, length = 32)
	private String phoneNumber;

	@Column(nullable = false, length = 300)
	private String address;

	@Column(nullable = false, length = 100)
	private String city;

	@Column(nullable = false, length = 100)
	private String region;

	@Column(name = "postal_code", length = 20)
	private String postalCode;

	@Column(name = "country_code", nullable = false, length = 2)
	private String countryCode;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

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

	public static Organisation createActive(
			String name,
			String legalName,
			OrganisationType type,
			String contactEmail,
			String phoneNumber,
			String address,
			String city,
			String region,
			String postalCode,
			String countryCode,
			String timeZone,
			UUID actorUserId,
			Instant createdAt) {
		Organisation organisation = new Organisation();
		organisation.id = UUID.randomUUID();
		organisation.name = required(name, 160, "name");
		organisation.normalizedName = normalizeName(organisation.name);
		organisation.legalName = optional(legalName, 200, "legalName");
		organisation.type = Objects.requireNonNull(type, "type must not be null");
		organisation.status = OrganisationStatus.ACTIVE;
		organisation.contactEmail = required(
				contactEmail,
				254,
				"contactEmail").toLowerCase(Locale.ROOT);
		organisation.phoneNumber = required(
				phoneNumber,
				32,
				"phoneNumber");
		organisation.address = required(address, 300, "address");
		organisation.city = required(city, 100, "city");
		organisation.region = required(region, 100, "region");
		organisation.postalCode = optional(postalCode, 20, "postalCode");
		organisation.countryCode = required(
				countryCode,
				2,
				"countryCode").toUpperCase(Locale.ROOT);
		organisation.timeZone = required(timeZone, 64, "timeZone");
		organisation.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		organisation.updatedBy = actorUserId;
		organisation.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		organisation.updatedAt = createdAt;
		return organisation;
	}

	private static String normalizeName(String value) {
		return value.strip()
				.replaceAll("\\s+", " ")
				.toLowerCase(Locale.ROOT);
	}

	public void updateProfile(String name, String contactEmail, String phoneNumber,
			String address, String city, String region, String postalCode,
			String countryCode, UUID actorId, Instant now) {
		this.name = required(name, 160, "name");
		this.normalizedName = normalizeName(this.name);
		this.contactEmail = required(contactEmail, 254, "contactEmail").toLowerCase(Locale.ROOT);
		this.phoneNumber = required(phoneNumber, 32, "phoneNumber");
		this.address = required(address, 300, "address");
		this.city = required(city, 100, "city");
		this.region = required(region, 100, "region");
		this.postalCode = optional(postalCode, 20, "postalCode");
		this.countryCode = required(countryCode, 2, "countryCode").toUpperCase(Locale.ROOT);
		this.updatedBy = Objects.requireNonNull(actorId);
		this.updatedAt = Objects.requireNonNull(now);
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
		if (value == null || value.isBlank()) {
			return null;
		}
		return required(value, maximumLength, fieldName);
	}
}
