package com.sahha.organisation.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.organisation.entity.DepartmentStatus;

public record DepartmentResponse(
		UUID id,
		UUID organisationId,
		String name,
		String code,
		String description,
		DepartmentStatus status,
		UUID createdBy,
		UUID updatedBy,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
