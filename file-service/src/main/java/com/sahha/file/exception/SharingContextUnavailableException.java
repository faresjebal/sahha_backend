package com.sahha.file.exception;

public class SharingContextUnavailableException extends RuntimeException {
    public SharingContextUnavailableException() {
        super("Shared resource eligibility could not be verified.");
    }
}
