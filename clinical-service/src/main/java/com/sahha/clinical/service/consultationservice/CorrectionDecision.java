package com.sahha.clinical.service.consultationservice;

public record CorrectionDecision(
		String fieldName,
		String oldValue,
		String newValue) {
}
