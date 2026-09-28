package com.sahha.communication.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.sahha.communication.entity.ShareResourceType;

public record ShareAccessDecisionRequest(
		@NotNull UUID patientRegistrationId,
		@NotNull ShareResourceType resourceType,
		@NotNull UUID resourceId,
		@NotNull UUID resourceOwnerUserId,
		UUID resourceOwnerMembershipId) {
}
