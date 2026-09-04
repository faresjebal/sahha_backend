package com.sahha.organisation.dto.response;

import java.util.List;

public record CollaborationDoctorPageResponse(
		List<SchedulingDoctorResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public CollaborationDoctorPageResponse {
		content = List.copyOf(content);
	}
}
