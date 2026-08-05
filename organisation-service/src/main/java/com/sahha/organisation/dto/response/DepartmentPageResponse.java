package com.sahha.organisation.dto.response;

import java.util.List;

public record DepartmentPageResponse(
		List<DepartmentResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
