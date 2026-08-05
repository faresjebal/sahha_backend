package com.sahha.organisation.exception;

public class ConcurrentDepartmentModificationException extends RuntimeException {

	public ConcurrentDepartmentModificationException() {
		super("The department was modified by another request");
	}
}
