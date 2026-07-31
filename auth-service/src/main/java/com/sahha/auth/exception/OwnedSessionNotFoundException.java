package com.sahha.auth.exception;

public class OwnedSessionNotFoundException extends RuntimeException {

	public OwnedSessionNotFoundException() {
		super("owned session was not found");
	}
}
