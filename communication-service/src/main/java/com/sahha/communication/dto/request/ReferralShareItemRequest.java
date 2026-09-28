package com.sahha.communication.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.sahha.communication.entity.ShareResourceType;

public record ReferralShareItemRequest(
		@NotNull ShareResourceType resourceType,
		@NotNull UUID resourceId) {
}
