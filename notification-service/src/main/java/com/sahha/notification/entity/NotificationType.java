package com.sahha.notification.entity;

import java.util.Optional;

import com.sahha.notification.event.AppointmentEventType;

public enum NotificationType {
	APPOINTMENT_REQUESTED,
	APPOINTMENT_RESCHEDULED,
	APPOINTMENT_CANCELLED,
	PATIENT_CHECKED_IN,
	MESSAGE_RECEIVED;

	public static Optional<NotificationType> forDoctor(
			AppointmentEventType eventType) {
		return switch (eventType) {
			case APPOINTMENT_REQUESTED -> Optional.of(APPOINTMENT_REQUESTED);
			case APPOINTMENT_RESCHEDULED -> Optional.of(APPOINTMENT_RESCHEDULED);
			case APPOINTMENT_CANCELLED -> Optional.of(APPOINTMENT_CANCELLED);
			case APPOINTMENT_CHECKED_IN -> Optional.of(PATIENT_CHECKED_IN);
			case APPOINTMENT_CONFIRMED,
					APPOINTMENT_REJECTED,
					APPOINTMENT_STARTED,
					APPOINTMENT_COMPLETED,
					APPOINTMENT_NO_SHOW -> Optional.empty();
		};
	}
}
