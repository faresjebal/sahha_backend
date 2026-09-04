package com.sahha.clinical.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import com.sahha.clinical.entity.CorrectionTargetType;

public record CreateClinicalCorrectionRequest(
		@NotNull @PositiveOrZero Long version,
		@NotNull CorrectionTargetType targetType,
		UUID targetId,
		@NotBlank @Size(max = 64) String fieldName,
		@Size(max = 20000) String newValue,
		@NotBlank @Size(max = 1000) String reason) {
}
