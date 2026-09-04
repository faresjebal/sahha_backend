package com.sahha.notification.service.inappnotificationservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.notification.client.organisation.OrganisationContextClient;
import com.sahha.notification.client.organisation.OrganisationContextResource;
import com.sahha.notification.exception.NotificationAccessDeniedException;

@Service
public class NotificationAccessService {

	private final OrganisationContextClient organisationContextClient;

	public NotificationAccessService(
			OrganisationContextClient organisationContextClient) {
		this.organisationContextClient = organisationContextClient;
	}

	public void requireActiveMembership(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		if (organisationId == null || actorUserId == null
				|| accessToken == null || accessToken.isBlank()) {
			throw new NotificationAccessDeniedException();
		}
		OrganisationContextResource context = organisationContextClient.resolve(
				organisationId, accessToken);
		if (!organisationId.equals(context.organisationId())
				|| context.roles().isEmpty()) {
			throw new NotificationAccessDeniedException();
		}
	}
}
