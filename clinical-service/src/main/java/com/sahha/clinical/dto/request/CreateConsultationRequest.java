package com.sahha.clinical.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record CreateConsultationRequest(
		@NotNull UUID appointmentId) {
}
