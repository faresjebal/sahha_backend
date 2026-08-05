package com.sahha.organisation.dto.response;

import java.util.List;

public record StaffMemberPageResponse(
		List<StaffMemberResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
