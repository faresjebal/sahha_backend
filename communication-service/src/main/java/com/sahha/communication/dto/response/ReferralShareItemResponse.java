package com.sahha.communication.dto.response;

import java.util.UUID;

import com.sahha.communication.entity.ShareResourceType;

public record ReferralShareItemResponse(
		UUID id, ShareResourceType resourceType, UUID resourceId) {
}
