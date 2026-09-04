package com.sahha.clinical.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FinalizeConsultationRequest(
		@NotNull @PositiveOrZero Long version) {
}
