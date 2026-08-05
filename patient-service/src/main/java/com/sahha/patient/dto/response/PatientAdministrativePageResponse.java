package com.sahha.patient.dto.response;

import java.util.List;

public record PatientAdministrativePageResponse(
		List<PatientAdministrativeSummaryResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
