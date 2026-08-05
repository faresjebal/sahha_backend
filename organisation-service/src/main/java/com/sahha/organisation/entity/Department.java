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
@Table(name = "department")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class Department {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(nullable = false, length = 120)
	@ToString.Include
	private String name;

	@Column(name = "normalized_name", nullable = false, length = 120)
	private String normalizedName;

	@Column(nullable = false, length = 32)
	@ToString.Include
	private String code;

	@Column(length = 500)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private DepartmentStatus status;

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

	public static Department create(
			UUID organisationId,
			String name,
			String code,
			String description,
			UUID actorUserId,
			Instant createdAt) {
		Department department = new Department();
		department.id = UUID.randomUUID();
		department.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		department.setDetails(name, code, description);
		department.status = DepartmentStatus.ACTIVE;
		department.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		department.updatedBy = actorUserId;
		department.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		department.updatedAt = createdAt;
		return department;
	}

	public void updateDetails(
			String name,
			String code,
			String description,
			UUID actorUserId,
			Instant updatedAt) {
		setDetails(name, code, description);
		touch(actorUserId, updatedAt);
	}

	public boolean changeStatus(
			DepartmentStatus status,
			UUID actorUserId,
			Instant updatedAt) {
		DepartmentStatus requiredStatus = Objects.requireNonNull(
				status,
				"status must not be null");
		if (this.status == requiredStatus) {
			return false;
		}
		this.status = requiredStatus;
		touch(actorUserId, updatedAt);
		return true;
	}

	private void setDetails(String name, String code, String description) {
		this.name = required(name, 120, "name");
		this.normalizedName = this.name.toLowerCase(Locale.ROOT);
		this.code = required(code, 32, "code").toUpperCase(Locale.ROOT);
		this.description = optional(description, 500, "description");
	}

	private void touch(UUID actorUserId, Instant updatedAt) {
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
