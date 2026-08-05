package com.sahha.organisation.entity;

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
@Table(name = "staff_department_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class StaffDepartmentAssignment {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "membership_id", nullable = false, updatable = false)
	private UUID membershipId;

	@Column(name = "department_id", nullable = false, updatable = false)
	private UUID departmentId;

	@Column(name = "position_title", nullable = false, length = 120)
	private String positionTitle;

	@Column(name = "primary_assignment", nullable = false)
	private boolean primaryAssignment;

	@Column(name = "start_date", nullable = false, updatable = false)
	private LocalDate startDate;

	@Column(name = "planned_end_date")
	private LocalDate plannedEndDate;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private StaffDepartmentAssignmentStatus status;

	@Column(name = "ended_at")
	private Instant endedAt;

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

	public static StaffDepartmentAssignment assign(
			UUID organisationId,
			UUID membershipId,
			UUID departmentId,
			String positionTitle,
			boolean primaryAssignment,
			LocalDate startDate,
			LocalDate plannedEndDate,
			UUID actorUserId,
			Instant createdAt) {
		StaffDepartmentAssignment assignment =
				new StaffDepartmentAssignment();
		assignment.id = UUID.randomUUID();
		assignment.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		assignment.membershipId = Objects.requireNonNull(
				membershipId,
				"membershipId must not be null");
		assignment.departmentId = Objects.requireNonNull(
				departmentId,
				"departmentId must not be null");
		assignment.positionTitle = required(
				positionTitle,
				120,
				"positionTitle");
		assignment.primaryAssignment = primaryAssignment;
		assignment.startDate = Objects.requireNonNull(
				startDate,
				"startDate must not be null");
		assignment.plannedEndDate = validateEndDate(
				assignment.startDate,
				plannedEndDate);
		assignment.status = StaffDepartmentAssignmentStatus.ACTIVE;
		assignment.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		assignment.updatedBy = actorUserId;
		assignment.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		assignment.updatedAt = createdAt;
		return assignment;
	}

	public boolean end(
			LocalDate endDate,
			UUID actorUserId,
			Instant endedAt) {
		if (status == StaffDepartmentAssignmentStatus.ENDED) {
			return false;
		}
		plannedEndDate = validateEndDate(
				startDate,
				Objects.requireNonNull(endDate, "endDate must not be null"));
		status = StaffDepartmentAssignmentStatus.ENDED;
		this.endedAt = requireTimestamp(endedAt);
		updatedBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		updatedAt = this.endedAt;
		return true;
	}

	private Instant requireTimestamp(Instant value) {
		Instant required = Objects.requireNonNull(
				value,
				"timestamp must not be null");
		if (required.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					"timestamp must not precede creation");
		}
		return required;
	}

	private static LocalDate validateEndDate(
			LocalDate startDate,
			LocalDate endDate) {
		if (endDate != null && endDate.isBefore(startDate)) {
			throw new IllegalArgumentException(
					"plannedEndDate must not precede startDate");
		}
		return endDate;
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
}
