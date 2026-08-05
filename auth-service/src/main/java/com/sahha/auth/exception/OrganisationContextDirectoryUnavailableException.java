package com.sahha.auth.exception;

public class OrganisationContextDirectoryUnavailableException
		extends RuntimeException {

	public OrganisationContextDirectoryUnavailableException() {
		super("The organisation context directory is unavailable.");
	}
}
