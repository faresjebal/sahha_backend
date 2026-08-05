package com.sahha.patient.exception;

import com.sahha.patient.dto.response.DuplicateCheckResponse;

public class PossibleDuplicateException extends RuntimeException {

	private final DuplicateCheckResponse duplicateCheck;

	public PossibleDuplicateException(DuplicateCheckResponse duplicateCheck) {
		this.duplicateCheck = duplicateCheck;
	}

	public DuplicateCheckResponse duplicateCheck() {
		return duplicateCheck;
	}
}
