package com.sahha.clinical.service.consultationservice;

import com.sahha.clinical.dto.response.ConsultationResponse;

public record ConsultationCreationResult(
		ConsultationResponse consultation,
		boolean created) {
}
