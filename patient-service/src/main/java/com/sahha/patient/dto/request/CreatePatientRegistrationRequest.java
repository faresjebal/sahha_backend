package com.sahha.patient.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sahha.patient.entity.DuplicateDecision;
import com.sahha.patient.entity.DuplicateDecisionReason;
import com.sahha.patient.entity.PatientSex;

public record CreatePatientRegistrationRequest(
		@NotBlank @Size(max = 80) String firstName,
		@NotBlank @Size(max = 80) String lastName,
		@NotNull @PastOrPresent LocalDate dateOfBirth,
		@NotNull PatientSex sex,
		@Valid PatientIdentifierRequest identifier,
		@NotBlank @Size(max = 32) String phoneNumber,
		@Email @Size(max = 254) String email,
		@NotBlank @Size(max = 300) String address,
		@Size(max = 100) String city,
		@Size(max = 100) String region,
		@Size(max = 20) String postalCode,
		@NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
		@Size(max = 160) String emergencyContactName,
		@Size(max = 32) String emergencyContactPhone,
		@Size(max = 80) String emergencyContactRelationship,
		@Size(max = 64) String preferredLanguage,
		@Size(max = 500) String accessibilityNeeds,
		@AssertTrue boolean privacyNoticeAcknowledged,
		DuplicateDecision duplicateDecision,
		UUID selectedPatientId,
		DuplicateDecisionReason duplicateDecisionReason) {
}
