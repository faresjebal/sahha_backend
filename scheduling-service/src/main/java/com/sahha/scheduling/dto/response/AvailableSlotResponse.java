package com.sahha.scheduling.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record AvailableSlotResponse(
		Instant startsAt,
		Instant endsAt,
		LocalDate localDate,
		LocalTime localStartTime,
		LocalTime localEndTime) {
}
