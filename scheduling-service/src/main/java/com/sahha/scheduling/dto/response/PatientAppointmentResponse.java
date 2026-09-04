package com.sahha.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.scheduling.entity.AppointmentStatus;

public record PatientAppointmentResponse(
		UUID id,
		UUID organisationId,
		UUID doctorUserId,
		AppointmentStatus status,
		String statusReason,
		Instant startsAt,
		Instant endsAt,
		String timeZone,
		String locationLabel,
		Instant bookedAt,
		Instant updatedAt,
		long version) {
}
