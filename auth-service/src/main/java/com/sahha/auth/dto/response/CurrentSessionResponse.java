package com.sahha.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.oauth2.jwt.Jwt;

import com.sahha.auth.security.SessionBoundJwtValidator;

@Schema(
		name = "CurrentSessionResponse",
		description = """
				Non-secret metadata reconstructed from an already validated access \
				cookie. Reading it never rotates or issues credentials.
				""")
public record CurrentSessionResponse(
		@Schema(example = "8c3c4db9-9d36-4f53-9bea-c07bb45ebbea")
		UUID userId,

		@Schema(example = "a8c42991-ce66-4f66-842f-262390359515")
		UUID sessionId,

		Instant accessTokenExpiresAt,

		@Schema(example = "[\"PLATFORM_ADMIN\"]")
		List<String> platformRoles) {

	public static CurrentSessionResponse from(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(
				SessionBoundJwtValidator.PLATFORM_ROLES_CLAIM);
		return new CurrentSessionResponse(
				UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(
						SessionBoundJwtValidator.SESSION_ID_CLAIM)),
				jwt.getExpiresAt(),
				roles == null ? List.of() : List.copyOf(roles));
	}
}
