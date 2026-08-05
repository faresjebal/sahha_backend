package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
		@NotBlank
		@Size(max = 120)
		String name,
		@NotBlank
		@Size(max = 32)
		@Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]{1,31}$")
		String code,
		@Size(max = 500)
		String description,
		@NotNull
		@PositiveOrZero
		Long version) {
}
