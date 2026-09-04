package com.sahha.scheduling.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.scheduling.entity.AppointmentStatus;
import com.sahha.scheduling.entity.AppointmentActorType;

public record AppointmentResponse(
		UUID id,
		UUID organisationId,
		UUID bookingRequestId,
		UUID patientRegistrationId,
		UUID patientId,
		UUID doctorUserId,
		UUID doctorMembershipId,
		AppointmentStatus status,
		String statusReason,
		Instant startsAt,
		Instant endsAt,
		String timeZone,
		String locationLabel,
		UUID bookedByUserId,
		AppointmentActorType bookedByActorType,
		UUID bookedByMembershipId,
		Instant bookedAt,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
