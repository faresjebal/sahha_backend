package com.sahha.scheduling.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AvailableSlotsResponse(
		UUID organisationId,
		UUID doctorUserId,
		LocalDate from,
		LocalDate to,
		String timeZone,
		String locationLabel,
		int appointmentDurationMinutes,
		List<AvailableSlotResponse> slots) {
}
