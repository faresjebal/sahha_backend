package com.sahha.patient.client.auth;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.patient.config.PatientSecurityProperties;
import com.sahha.patient.exception.AuthAccountUnavailableException;
import com.sahha.patient.exception.PatientAccountLinkNotFoundException;

@Component
public class AuthAccountClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public AuthAccountClient(
			@Qualifier("patientAuthAccountRestClient") RestClient restClient,
			PatientSecurityProperties securityProperties) {
		this.restClient = restClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public AuthAccountResource currentAccount(String accessToken) {
		Objects.requireNonNull(accessToken, "accessToken must not be null");
		try {
			AuthAccountResource resource = restClient.get()
					.uri("/api/v1/auth/account")
					.header(HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(AuthAccountResource.class);
			if (resource == null || resource.id() == null) {
				throw new AuthAccountUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new PatientAccountLinkNotFoundException();
		}
		catch (RestClientException unavailable) {
			throw new AuthAccountUnavailableException();
		}
	}
}
