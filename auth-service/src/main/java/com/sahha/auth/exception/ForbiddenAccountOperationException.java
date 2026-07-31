package com.sahha.auth.exception;

public class ForbiddenAccountOperationException extends RuntimeException {

	public ForbiddenAccountOperationException() {
		super("account operation is forbidden");
	}
}
