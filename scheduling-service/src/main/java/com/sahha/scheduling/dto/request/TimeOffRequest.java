package com.sahha.scheduling.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TimeOffRequest(
		@NotNull LocalDate date,
		LocalTime startTime,
		LocalTime endTime,
		@NotBlank @Size(max = 160) String reason) {
}
