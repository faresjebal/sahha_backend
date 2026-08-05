package com.sahha.patient.client.organisation;

import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.patient.config.PatientSecurityProperties;
import com.sahha.patient.exception.OrganisationContextUnavailableException;
import com.sahha.patient.exception.PatientAccessDeniedException;

@Component
public class OrganisationContextClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public OrganisationContextClient(
			RestClient organisationContextRestClient,
			PatientSecurityProperties securityProperties) {
		this.restClient = organisationContextRestClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public OrganisationContextResource resolve(
			UUID organisationId,
			String accessToken) {
		Objects.requireNonNull(organisationId, "organisationId must not be null");
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
					|| resource.organisationId() == null
					|| resource.roles() == null) {
				throw new OrganisationContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound
				| HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new PatientAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new OrganisationContextUnavailableException();
		}
	}
}
