package com.sahha.clinical.client.scheduling;

import java.util.UUID;

public record ClinicalAppointmentCompletionResource(
		UUID eventId,
		UUID appointmentId,
		String outcome,
		boolean duplicate,
		String appointmentStatus,
		Long appointmentVersion,
		String conflictCode) {
}
