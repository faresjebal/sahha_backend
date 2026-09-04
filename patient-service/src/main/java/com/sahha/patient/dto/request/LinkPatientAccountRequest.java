package com.sahha.patient.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;

public record LinkPatientAccountRequest(
		@NotNull UUID organisationId,
		@NotBlank
		@Pattern(regexp = "^PT-[0-9A-F]{12}$")
		String medicalRecordNumber,
		@NotNull @Past LocalDate dateOfBirth) {
}
