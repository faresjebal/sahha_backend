package com.sahha.communication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReferralReasonedCommandRequest(
		@PositiveOrZero long expectedVersion,
		@NotBlank @Size(min = 3, max = 500) String reason) {
}
