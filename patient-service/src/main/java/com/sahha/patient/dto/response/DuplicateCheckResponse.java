package com.sahha.patient.dto.response;

import java.util.List;

public record DuplicateCheckResponse(
		boolean reviewRequired,
		boolean exactStrongIdentifierMatch,
		List<DuplicateCandidateResponse> candidates) {
}
