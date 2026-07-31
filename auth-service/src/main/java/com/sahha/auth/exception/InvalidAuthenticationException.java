package com.sahha.auth.exception;

public class InvalidAuthenticationException extends RuntimeException {

	public InvalidAuthenticationException() {
		super("authentication credentials are invalid");
	}
}
