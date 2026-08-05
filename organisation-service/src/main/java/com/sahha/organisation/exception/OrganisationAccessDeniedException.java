package com.sahha.organisation.exception;

public class OrganisationAccessDeniedException extends RuntimeException {

	public OrganisationAccessDeniedException() {
		super("The active organisation context does not permit this operation");
	}
}
