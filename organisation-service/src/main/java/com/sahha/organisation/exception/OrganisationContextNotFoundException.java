package com.sahha.organisation.exception;

public class OrganisationContextNotFoundException extends RuntimeException {

	public OrganisationContextNotFoundException() {
		super("No active organisation context is available.");
	}
}
