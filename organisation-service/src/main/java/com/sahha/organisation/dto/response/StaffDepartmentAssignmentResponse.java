package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.sahha.organisation.entity.StaffDepartmentAssignmentStatus;

public record StaffDepartmentAssignmentResponse(
		UUID id,
		UUID departmentId,
		String departmentName,
		String departmentCode,
		String positionTitle,
		boolean primaryAssignment,
		LocalDate startDate,
		LocalDate plannedEndDate,
		StaffDepartmentAssignmentStatus status,
		Instant endedAt,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
