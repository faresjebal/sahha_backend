package com.sahha.organisation.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record EndStaffDepartmentAssignmentRequest(
		@NotNull
		LocalDate endDate,
		@PositiveOrZero
		long version) {
}
