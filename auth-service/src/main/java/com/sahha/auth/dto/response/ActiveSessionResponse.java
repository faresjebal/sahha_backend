package com.sahha.auth.dto.response;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.sahha.auth.service.usersessionservice.UserSessionSummary;

public record ActiveSessionResponse(
		@Schema(example = "a23d5c4f-bad0-4f89-a7b4-0cb7cb4d9158")
		UUID sessionId,
		@Schema(example = "Personal laptop")
		String deviceName,
		Instant createdAt,
		Instant lastActivityAt,
		Instant idleExpiresAt,
		Instant absoluteExpiresAt,
		boolean current) {

	public static ActiveSessionResponse from(UserSessionSummary session) {
		return new ActiveSessionResponse(
				session.sessionId(),
				session.deviceName(),
				session.createdAt(),
				session.lastActivityAt(),
				session.idleExpiresAt(),
				session.absoluteExpiresAt(),
				session.current());
	}
}
