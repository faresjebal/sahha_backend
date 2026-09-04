package com.sahha.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.scheduling.entity.AppointmentStatus;

public record ClinicalAppointmentContextResponse(
		UUID appointmentId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		AppointmentStatus status,
		Instant startsAt,
		Instant endsAt,
		long version) {
}
