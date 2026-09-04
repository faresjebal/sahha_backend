package com.sahha.scheduling.dto.response;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record WeeklyAvailabilityResponse(
		UUID id,
		DayOfWeek dayOfWeek,
		LocalTime startTime,
		LocalTime endTime) {
}
