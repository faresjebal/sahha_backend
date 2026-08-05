package com.sahha.auth.client.organisation;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.auth.config.AuthCookieProperties;
import com.sahha.auth.exception.InvalidOrganisationContextException;
import com.sahha.auth.exception.OrganisationContextDirectoryUnavailableException;

@Component
public class OrganisationContextClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public OrganisationContextClient(
			RestClient organisationContextRestClient,
			AuthCookieProperties cookieProperties) {
		this.restClient = organisationContextRestClient;
		this.accessTokenCookieName = cookieProperties.accessTokenName();
	}

	public OrganisationContextResource resolve(
			UUID organisationId,
			String accessToken) {
		Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		Objects.requireNonNull(accessToken, "accessToken must not be null");
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
			if (resource == null
					|| !organisationId.equals(resource.organisationId())
					|| resource.membershipId() == null
					|| resource.organisationName() == null
					|| resource.organisationType() == null
					|| resource.roles() == null
					|| resource.roles().isEmpty()) {
				throw new OrganisationContextDirectoryUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new InvalidOrganisationContextException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new InvalidOrganisationContextException();
		}
		catch (RestClientException unavailable) {
			throw new OrganisationContextDirectoryUnavailableException();
		}
	}
}
