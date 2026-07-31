package com.sahha.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.sahha.auth.service.usersessionservice.IssuedBrowserSession;

@Schema(
		name = "AuthenticatedSessionResponse",
		description = """
				Non-secret session metadata. Access and refresh credentials are \
				delivered only through HttpOnly cookies.
				""")
public record AuthenticatedSessionResponse(
		@Schema(example = "8c3c4db9-9d36-4f53-9bea-c07bb45ebbea")
		UUID userId,

		@Schema(example = "a8c42991-ce66-4f66-842f-262390359515")
		UUID sessionId,

		Instant accessTokenExpiresAt,
		Instant refreshTokenExpiresAt,
		Instant sessionIdleExpiresAt,
		Instant sessionAbsoluteExpiresAt,

		@Schema(example = "[\"PLATFORM_ADMIN\"]")
		List<String> platformRoles) {

	public static AuthenticatedSessionResponse from(
			IssuedBrowserSession session) {
		return new AuthenticatedSessionResponse(
				session.getUserId(),
				session.getSessionId(),
				session.getAccessTokenExpiresAt(),
				session.getRefreshTokenExpiresAt(),
				session.getIdleExpiresAt(),
				session.getAbsoluteExpiresAt(),
				session.getPlatformRoles());
	}
}
