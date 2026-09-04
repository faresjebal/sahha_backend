package com.sahha.clinical.client.scheduling;

import java.time.Instant;
import java.util.UUID;

public record ClinicalCompletionRecoveryCommand(
		UUID eventId,
		UUID consultationId,
		long clinicalResourceVersion,
		Instant occurredAt) {
}
