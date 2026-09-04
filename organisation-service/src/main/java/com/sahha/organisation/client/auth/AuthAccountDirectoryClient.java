package com.sahha.organisation.client.auth;

import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.organisation.config.OrganisationSecurityProperties;
import com.sahha.organisation.exception.AuthAccountDirectoryUnavailableException;
import com.sahha.organisation.exception.EligibleAccountNotFoundException;

@Component
public class AuthAccountDirectoryClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public AuthAccountDirectoryClient(
			RestClient authAccountRestClient,
			OrganisationSecurityProperties securityProperties) {
		this.restClient = authAccountRestClient;
		this.accessTokenCookieName = securityProperties
				.accessTokenCookieName();
	}

	public AuthAccountResource findByEmail(
			String email,
			String accessToken) {
		Objects.requireNonNull(email, "email must not be null");
		Objects.requireNonNull(accessToken, "accessToken must not be null");
		try {
			AuthAccountResource resource = restClient.get()
					.uri(
							"/api/v1/auth/platform/accounts?email={email}",
							email)
					.header(
						HttpHeaders.COOKIE,
						accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(AuthAccountResource.class);
			if (resource == null || resource.id() == null) {
				throw new AuthAccountDirectoryUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new EligibleAccountNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new AuthAccountDirectoryUnavailableException();
		}
		catch (RestClientException unavailable) {
			throw new AuthAccountDirectoryUnavailableException();
		}
	}

	public AuthAccountResource currentAccount(String accessToken) {
		Objects.requireNonNull(accessToken, "accessToken must not be null");
		try {
			AuthAccountResource resource = restClient.get()
					.uri("/api/v1/auth/account")
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(AuthAccountResource.class);
			if (resource == null || resource.id() == null) {
				throw new AuthAccountDirectoryUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new AuthAccountDirectoryUnavailableException();
		}
		catch (RestClientException unavailable) {
			throw new AuthAccountDirectoryUnavailableException();
		}
	}
}
