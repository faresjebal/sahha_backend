package com.sahha.clinical.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.clinical.entity.ConsultationStatus;

public record ConsultationResponse(
		UUID id,
		UUID organisationId,
		UUID appointmentId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		ConsultationStatus status,
		String reasonForConsultation,
		String draftNotes,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
