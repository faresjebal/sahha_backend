package com.sahha.notification.event;

public class InvalidAppointmentEventException extends RuntimeException {

	private final String reasonCode;

	public InvalidAppointmentEventException(String reasonCode) {
		super("Appointment event was rejected: " + reasonCode);
		this.reasonCode = reasonCode;
	}

	public String getReasonCode() {
		return reasonCode;
	}
}
