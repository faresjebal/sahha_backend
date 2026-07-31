package com.sahha.organisation.exception;

public class OrganisationNotFoundException extends RuntimeException {

	public OrganisationNotFoundException() {
		super("organisation not found");
	}
}
