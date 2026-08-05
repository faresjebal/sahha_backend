package com.sahha.organisation.dto.request;

import jakarta.validation.constraints.PositiveOrZero;

public record StaffInvitationVersionRequest(
		@PositiveOrZero long version) {
}
