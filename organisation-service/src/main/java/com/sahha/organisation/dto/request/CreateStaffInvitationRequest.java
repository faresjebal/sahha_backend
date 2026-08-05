package com.sahha.organisation.dto.request;

import com.sahha.organisation.entity.OrganisationRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStaffInvitationRequest(
		@NotNull
		@Email
		@Size(max = 254)
		String email,
		@NotNull
		OrganisationRole role) {
}
