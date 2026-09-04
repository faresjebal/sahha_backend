package com.sahha.file.exception;

public class FileScanConflictException extends RuntimeException {

	public FileScanConflictException() {
		super("The file cannot accept this scan decision.");
	}
}
