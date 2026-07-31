package com.sahha.auth.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("refresh token is invalid or expired");
	}
}
