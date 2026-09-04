package com.sahha.clinical.dto.response;

import java.util.UUID;

public record AppointmentCompletionRecoveryResponse(
		UUID consultationId,
		UUID appointmentId,
		String outcome,
		boolean duplicate,
		String appointmentStatus,
		Long appointmentVersion) {
}
