package com.sahha.auth.service.usersessionservice;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.Getter;
import lombok.ToString;

import com.sahha.auth.client.organisation.OrganisationContextResource;
import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class IssuedActiveOrganisationSession {

	@ToString.Include
	private final UUID userId;

	@ToString.Include
	private final UUID sessionId;

	private final IssuedAccessToken accessToken;
	private final List<String> platformRoles;
	private final OrganisationContextResource organisationContext;

	public IssuedActiveOrganisationSession(
			UUID userId,
			UUID sessionId,
			IssuedAccessToken accessToken,
			List<String> platformRoles,
			OrganisationContextResource organisationContext) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.sessionId = Objects.requireNonNull(
				sessionId,
				"sessionId must not be null");
		this.accessToken = Objects.requireNonNull(
				accessToken,
				"accessToken must not be null");
		this.platformRoles = List.copyOf(Objects.requireNonNull(
				platformRoles,
				"platformRoles must not be null"));
		this.organisationContext = Objects.requireNonNull(
				organisationContext,
				"organisationContext must not be null");
	}
}
