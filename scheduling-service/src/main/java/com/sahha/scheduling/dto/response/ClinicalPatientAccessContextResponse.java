package com.sahha.scheduling.dto.response;

import java.util.UUID;

import com.sahha.scheduling.entity.AppointmentStatus;

public record ClinicalPatientAccessContextResponse(
		UUID appointmentId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		AppointmentStatus status,
		long appointmentVersion) {
}
