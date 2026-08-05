package com.sahha.organisation.dto.response;

import java.util.List;

public record OrganisationMembershipPageResponse(
		List<OrganisationMembershipResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages) {
}
