package com.sahha.communication.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ShareAccessDecisionResponse(
		boolean allowed,
		UUID grantId,
		UUID referralId,
		Instant validUntil) {
	public static ShareAccessDecisionResponse denied() {
		return new ShareAccessDecisionResponse(false, null, null, null);
	}
}
