package com.sahha.patient.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sahha.patient.entity.PatientIdentifierType;

public record PatientIdentifierRequest(
		@NotNull PatientIdentifierType type,
		@NotBlank @Size(min = 4, max = 60) String value,
		@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
}
