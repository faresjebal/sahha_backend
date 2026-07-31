package com.sahha.organisation.dto.response;

import java.util.List;

public record OrganisationPageResponse(
		List<OrganisationResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public OrganisationPageResponse {
		items = List.copyOf(items);
	}
}
