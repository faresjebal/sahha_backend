package com.sahha.auth.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.sahha.auth.client.organisation.OrganisationContextResource;
import com.sahha.auth.service.usersessionservice.IssuedActiveOrganisationSession;

@Schema(
		name = "ActiveOrganisationSessionResponse",
		description = "Renewed non-secret access context after an authorised organisation selection.")
public record ActiveOrganisationSessionResponse(
		UUID userId,
		UUID sessionId,
		Instant accessTokenExpiresAt,
		List<String> platformRoles,
		UUID activeOrganisationId,
		List<String> organisationRoles,
		UUID membershipId,
		String organisationName,
		String organisationType,
		long membershipVersion) {

	public static ActiveOrganisationSessionResponse from(
			IssuedActiveOrganisationSession session) {
		OrganisationContextResource context = session.getOrganisationContext();
		Set<String> contextRoles = context.roles();
		return new ActiveOrganisationSessionResponse(
				session.getUserId(),
				session.getSessionId(),
				session.getAccessToken().getExpiresAt(),
				session.getPlatformRoles(),
				context.organisationId(),
				contextRoles.stream().sorted().toList(),
				context.membershipId(),
				context.organisationName(),
				context.organisationType(),
				context.membershipVersion());
	}
}
