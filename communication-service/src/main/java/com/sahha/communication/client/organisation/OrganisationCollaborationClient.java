package com.sahha.communication.client.organisation;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.communication.config.CommunicationSecurityProperties;
import com.sahha.communication.exception.CommunicationAccessDeniedException;
import com.sahha.communication.exception.CommunicationContextUnavailableException;
import com.sahha.communication.exception.ConversationNotFoundException;

@Component
public class OrganisationCollaborationClient {
	private final RestClient restClient;
	private final String accessCookieName;

	public OrganisationCollaborationClient(
			@Qualifier("communicationOrganisationRestClient") RestClient restClient,
			CommunicationSecurityProperties properties) {
		this.restClient = restClient;
		this.accessCookieName = properties.accessTokenCookieName();
	}

	public CollaborationDoctorResource resolve(
			UUID organisationId, UUID doctorUserId, String accessToken) {
		Objects.requireNonNull(organisationId); Objects.requireNonNull(doctorUserId);
		Objects.requireNonNull(accessToken);
		try {
			CollaborationDoctorResource value = restClient.get()
					.uri("/api/v1/organisations/{organisationId}/collaboration-doctors/{doctorUserId}",
							organisationId, doctorUserId)
					.header(HttpHeaders.COOKIE, accessCookieName + "=" + accessToken)
					.retrieve().body(CollaborationDoctorResource.class);
			if (value == null || !organisationId.equals(value.organisationId())
					|| !doctorUserId.equals(value.userId()) || value.membershipId() == null
					|| value.displayName() == null || value.displayName().isBlank()
					|| value.membershipVersion() < 0) {
				throw new CommunicationContextUnavailableException();
			}
			return value;
		}
		catch (HttpClientErrorException.NotFound missing) { throw new ConversationNotFoundException(); }
		catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden denied) {
			throw new CommunicationAccessDeniedException();
		}
		catch (RestClientException unavailable) { throw new CommunicationContextUnavailableException(); }
	}
}
