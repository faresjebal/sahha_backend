package com.sahha.scheduling.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReasonedAppointmentCommandRequest(
		@NotNull UUID commandRequestId,
		@Min(0) long version,
		@NotBlank @Size(max = 500) String reason) {
}
