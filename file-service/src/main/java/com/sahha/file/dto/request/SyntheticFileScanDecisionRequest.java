package com.sahha.file.dto.request;

import jakarta.validation.constraints.NotNull;

import com.sahha.file.service.medicalfilescanservice.FileScanDecision;

public record SyntheticFileScanDecisionRequest(
		@NotNull FileScanDecision decision) {
}
