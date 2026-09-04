package com.sahha.notification.service.appointmentnotificationservice;

public enum AppointmentEventProcessingResult {
	NOTIFICATION_CREATED,
	NO_ELIGIBLE_RECIPIENT,
	STALE,
	DUPLICATE
}
