package com.sahha.organisation.dto.request;

import com.sahha.organisation.entity.OrganisationMembershipStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeStaffMembershipStatusRequest(
		@NotNull
		OrganisationMembershipStatus status,
		@PositiveOrZero
		long version) {
}
