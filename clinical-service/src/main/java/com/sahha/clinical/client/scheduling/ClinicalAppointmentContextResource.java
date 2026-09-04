package com.sahha.clinical.client.scheduling;

import java.time.Instant;
import java.util.UUID;

public record ClinicalAppointmentContextResource(
		UUID appointmentId,
		UUID organisationId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		String status,
		Instant startsAt,
		Instant endsAt,
		long version) {
}
