package com.sahha.notification.event;

public class InvalidCommunicationEventException extends RuntimeException {
	private final String reasonCode;
	public InvalidCommunicationEventException(String reasonCode) {
		super(reasonCode);
		this.reasonCode = reasonCode;
	}
	public String getReasonCode() { return reasonCode; }
}
