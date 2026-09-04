package com.sahha.notification.client.organisation;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.notification.config.NotificationSecurityProperties;
import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.exception.OrganisationContextUnavailableException;

@Component
public class OrganisationContextClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public OrganisationContextClient(
			@Qualifier("notificationOrganisationContextRestClient")
					RestClient notificationOrganisationContextRestClient,
			NotificationSecurityProperties securityProperties) {
		this.restClient = notificationOrganisationContextRestClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public OrganisationContextResource resolve(
			UUID organisationId,
			String accessToken) {
		Objects.requireNonNull(organisationId);
		Objects.requireNonNull(accessToken);
		try {
			OrganisationContextResource resource = restClient.get()
					.uri(
							"/api/v1/organisations/{organisationId}/membership-context",
							organisationId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(OrganisationContextResource.class);
			if (resource == null || resource.membershipId() == null
					|| resource.organisationId() == null
					|| resource.roles() == null) {
				throw new OrganisationContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound
				| HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new NotificationAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new OrganisationContextUnavailableException();
		}
	}
}
