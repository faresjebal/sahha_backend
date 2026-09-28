package com.sahha.communication.dto.response;

import java.util.List;

public record ReferralPageResponse(
		List<ReferralResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
