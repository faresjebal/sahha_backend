package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.sahha.organisation.entity.DepartmentStatus;

public record ChangeDepartmentStatusRequest(
		@NotNull DepartmentStatus status,
		@NotNull @PositiveOrZero Long version) {
}
