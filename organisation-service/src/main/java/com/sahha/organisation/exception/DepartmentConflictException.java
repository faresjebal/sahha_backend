package com.sahha.organisation.exception;

public class DepartmentConflictException extends RuntimeException {

	public DepartmentConflictException() {
		super("Department name or code conflicts within the organisation");
	}
}
