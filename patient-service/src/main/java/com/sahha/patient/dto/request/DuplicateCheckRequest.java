package com.sahha.patient.dto.request;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import com.sahha.patient.entity.PatientSex;

public record DuplicateCheckRequest(
		@NotBlank @Size(max = 80) String firstName,
		@NotBlank @Size(max = 80) String lastName,
		@NotNull @PastOrPresent LocalDate dateOfBirth,
		@NotNull PatientSex sex,
		@NotBlank @Size(max = 32) String phoneNumber,
		@Email @Size(max = 254) String email,
		@Valid PatientIdentifierRequest identifier) {
}
