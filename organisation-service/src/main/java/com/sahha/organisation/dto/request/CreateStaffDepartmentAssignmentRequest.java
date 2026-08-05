package com.sahha.organisation.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffDepartmentAssignmentRequest(
		@NotNull
		UUID departmentId,
		@NotBlank
		@Size(max = 120)
		String positionTitle,
		boolean primaryAssignment,
		@NotNull
		LocalDate startDate,
		LocalDate plannedEndDate) {
}
