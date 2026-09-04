package com.sahha.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.event.AppointmentStatus;

public record NotificationResponse(
		UUID id,
		NotificationType notificationType,
		String resourceType,
		UUID resourceId,
		AppointmentStatus appointmentStatus,
		Instant appointmentStartsAt,
		Instant appointmentEndsAt,
		String appointmentTimeZone,
		String appointmentLocationLabel,
		long resourceVersion,
		Instant eventOccurredAt,
		Instant createdAt,
		boolean read,
		Instant readAt) {
}
