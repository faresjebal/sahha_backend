package com.sahha.scheduling.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TimeOffResponse(
		UUID id,
		LocalDate date,
		LocalTime startTime,
		LocalTime endTime,
		String reason) {
}
