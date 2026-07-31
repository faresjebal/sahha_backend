package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.UUID;

import com.sahha.auth.entity.UserSession;

public record UserSessionSummary(
		UUID sessionId,
		String deviceName,
		Instant createdAt,
		Instant lastActivityAt,
		Instant idleExpiresAt,
		Instant absoluteExpiresAt,
		boolean current) {

	static UserSessionSummary from(
			UserSession session,
			UUID currentSessionId) {
		return new UserSessionSummary(
				session.getId(),
				session.getDeviceName(),
				session.getCreatedAt(),
				session.getLastActivityAt(),
				session.getIdleExpiresAt(),
				session.getAbsoluteExpiresAt(),
				session.getId().equals(currentSessionId));
	}
}
