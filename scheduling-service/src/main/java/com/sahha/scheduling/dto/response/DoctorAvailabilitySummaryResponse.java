package com.sahha.scheduling.dto.response;

import java.util.UUID;

public record DoctorAvailabilitySummaryResponse(
		UUID doctorUserId,
		UUID doctorMembershipId,
		String timeZone,
		String locationLabel,
		int appointmentDurationMinutes) {
}
