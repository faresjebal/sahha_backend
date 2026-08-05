package com.sahha.organisation.exception;

public class DepartmentNotFoundException extends RuntimeException {

	public DepartmentNotFoundException() {
		super("Department not found");
	}
}
