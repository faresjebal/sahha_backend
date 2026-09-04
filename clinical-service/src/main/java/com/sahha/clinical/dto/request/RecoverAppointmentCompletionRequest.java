package com.sahha.clinical.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RecoverAppointmentCompletionRequest(
		@NotNull @PositiveOrZero Long version) {
}
