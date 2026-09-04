package com.sahha.scheduling.dto.response;

import java.util.UUID;

public record PatientDoctorAvailabilityResponse(
		UUID doctorUserId,
		String displayName,
		String timeZone,
		String locationLabel,
		int appointmentDurationMinutes) {
}
