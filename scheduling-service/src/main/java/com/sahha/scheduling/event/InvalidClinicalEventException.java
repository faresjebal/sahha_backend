package com.sahha.scheduling.event;

public class InvalidClinicalEventException extends RuntimeException {

	private final String reasonCode;

	public InvalidClinicalEventException(String reasonCode) {
		super(reasonCode);
		this.reasonCode = reasonCode;
	}

	public String getReasonCode() {
		return reasonCode;
	}
}
