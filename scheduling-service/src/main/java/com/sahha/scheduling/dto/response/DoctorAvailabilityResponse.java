package com.sahha.scheduling.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DoctorAvailabilityResponse(
		UUID id,
		UUID organisationId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		String timeZone,
		int appointmentDurationMinutes,
		int minimumLeadTimeMinutes,
		int bookingHorizonDays,
		String locationLabel,
		List<WeeklyAvailabilityResponse> weeklyWindows,
		List<AvailabilityBreakResponse> breaks,
		List<TimeOffResponse> timeOff,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
