package com.sahha.scheduling.dto.response;

import java.util.UUID;

import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.ClinicalCompletionOutcome;

public record ClinicalAppointmentCompletionResponse(
		UUID eventId,
		UUID appointmentId,
		ClinicalCompletionOutcome outcome,
		boolean duplicate,
		AppointmentStatus appointmentStatus,
		Long appointmentVersion,
		String conflictCode) {
}
