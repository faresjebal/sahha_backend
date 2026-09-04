package com.sahha.scheduling.dto.request;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record BookAppointmentRequest(
		@NotNull UUID bookingRequestId,
		@NotNull UUID patientRegistrationId,
		@NotNull UUID doctorUserId,
		@NotNull Instant startsAt) {
}
