package com.sahha.scheduling.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ClinicalCompletionRecoveryRequest(
		@NotNull UUID eventId,
		@NotNull UUID consultationId,
		@NotNull @PositiveOrZero Long clinicalResourceVersion,
		@NotNull Instant occurredAt) {
}
