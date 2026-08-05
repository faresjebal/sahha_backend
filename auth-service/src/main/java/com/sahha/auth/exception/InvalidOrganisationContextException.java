package com.sahha.auth.exception;

public class InvalidOrganisationContextException extends RuntimeException {

	public InvalidOrganisationContextException() {
		super("The requested organisation context is unavailable.");
	}
}
