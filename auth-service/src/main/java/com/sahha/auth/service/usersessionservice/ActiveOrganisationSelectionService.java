package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.auth.client.organisation.OrganisationContextClient;
import com.sahha.auth.client.organisation.OrganisationContextResource;

@Service
public class ActiveOrganisationSelectionService {

	private final OrganisationContextClient contextClient;
	private final ActiveOrganisationSessionService sessionService;

	public ActiveOrganisationSelectionService(
			OrganisationContextClient contextClient,
			ActiveOrganisationSessionService sessionService) {
		this.contextClient = contextClient;
		this.sessionService = sessionService;
	}

	public IssuedActiveOrganisationSession select(
			UUID userId,
			UUID sessionId,
			UUID organisationId,
			String currentAccessToken,
			Instant selectedAt) {
		OrganisationContextResource context = contextClient.resolve(
				organisationId,
				currentAccessToken);
		return sessionService.select(userId, sessionId, context, selectedAt);
	}
}
