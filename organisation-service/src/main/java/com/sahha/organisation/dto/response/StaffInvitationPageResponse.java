package com.sahha.organisation.dto.response;

import java.util.List;

public record StaffInvitationPageResponse(
		List<StaffInvitationResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
