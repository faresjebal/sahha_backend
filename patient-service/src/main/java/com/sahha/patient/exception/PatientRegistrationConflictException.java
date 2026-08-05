package com.sahha.patient.exception;

public class PatientRegistrationConflictException extends RuntimeException {

	public PatientRegistrationConflictException() {
	}

	public PatientRegistrationConflictException(String message) {
		super(message);
	}
}
