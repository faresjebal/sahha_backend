package com.sahha.scheduling.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AppointmentCommandRequest(
		@NotNull UUID commandRequestId,
		@Min(0) long version) {
}
