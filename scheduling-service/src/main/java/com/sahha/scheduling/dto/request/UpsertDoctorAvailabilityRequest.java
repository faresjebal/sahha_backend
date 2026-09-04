package com.sahha.scheduling.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpsertDoctorAvailabilityRequest(
		@NotBlank @Size(max = 64) String timeZone,
		@Min(5) @Max(240) int appointmentDurationMinutes,
		@Min(0) @Max(43200) int minimumLeadTimeMinutes,
		@Min(1) @Max(365) int bookingHorizonDays,
		@NotBlank @Size(max = 160) String locationLabel,
		@NotEmpty @Size(max = 28) List<@Valid WeeklyAvailabilityRequest> weeklyWindows,
		@Size(max = 28) List<@Valid AvailabilityBreakRequest> breaks,
		@Size(max = 100) List<@Valid TimeOffRequest> timeOff,
		@PositiveOrZero Long version) {
}
