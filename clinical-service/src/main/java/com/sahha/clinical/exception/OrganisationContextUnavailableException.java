package com.sahha.clinical.exception;

public class OrganisationContextUnavailableException extends RuntimeException {
    public OrganisationContextUnavailableException() { super("Organisation membership could not be verified"); }
}
