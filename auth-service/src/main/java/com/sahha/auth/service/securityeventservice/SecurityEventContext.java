package com.sahha.auth.service.securityeventservice;

import java.util.UUID;

public record SecurityEventContext(
		UUID userId,
		UUID subjectUserId,
		String attemptedEmail,
		UUID sessionId,
		UUID activeOrganisationId,
		String ipAddress,
		String userAgent) {

	public static SecurityEventContext account(UUID userId) {
		return new SecurityEventContext(
				userId,
				null,
				null,
				null,
				null,
				null,
				null);
	}

	public static SecurityEventContext session(
			UUID userId,
			UUID sessionId) {
		return new SecurityEventContext(
				userId,
				null,
				null,
				sessionId,
				null,
				null,
				null);
	}

	public static SecurityEventContext authentication(
			UUID userId,
			String attemptedEmail,
			UUID sessionId,
			String ipAddress,
			String userAgent) {
		return new SecurityEventContext(
				userId,
				null,
				attemptedEmail,
				sessionId,
				null,
				ipAddress,
				userAgent);
	}

	public static SecurityEventContext administration(
			UUID actorUserId,
			UUID subjectUserId) {
		return new SecurityEventContext(
				actorUserId,
				subjectUserId,
				null,
				null,
				null,
				null,
				null);
	}

	public static SecurityEventContext organisationSelection(
			UUID userId,
			UUID sessionId,
			UUID activeOrganisationId) {
		return new SecurityEventContext(
				userId,
				null,
				null,
				sessionId,
				activeOrganisationId,
				null,
				null);
	}
}
