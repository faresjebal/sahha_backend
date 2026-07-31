package com.sahha.auth.exception;

public class InvalidVerificationTokenException extends RuntimeException {

	public InvalidVerificationTokenException() {
		super("verification token is invalid or expired");
	}
}
