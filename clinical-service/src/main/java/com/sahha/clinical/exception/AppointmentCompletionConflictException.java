package com.sahha.clinical.exception;

public class AppointmentCompletionConflictException extends RuntimeException {

	private final String conflictCode;

	public AppointmentCompletionConflictException(String conflictCode) {
		super(conflictCode);
		this.conflictCode = conflictCode;
	}

	public String getConflictCode() {
		return conflictCode;
	}
}
