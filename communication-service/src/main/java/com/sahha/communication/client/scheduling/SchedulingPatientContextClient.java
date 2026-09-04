package com.sahha.communication.client.scheduling;

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
public class SchedulingPatientContextClient {
	private final RestClient restClient;
	private final String accessCookieName;

	public SchedulingPatientContextClient(
			@Qualifier("communicationSchedulingRestClient") RestClient restClient,
			CommunicationSecurityProperties properties) {
		this.restClient = restClient;
		this.accessCookieName = properties.accessTokenCookieName();
	}

	public void requireMentionable(UUID organisationId, UUID patientRegistrationId,
			UUID actorUserId, String accessToken) {
		Objects.requireNonNull(patientRegistrationId);
		try {
			PatientAccessContextResource value = restClient.get()
					.uri("/api/v1/internal/clinical/patients/{patientRegistrationId}/access-context",
							patientRegistrationId)
					.header(HttpHeaders.COOKIE, accessCookieName + "=" + accessToken)
					.retrieve().body(PatientAccessContextResource.class);
			if (value == null || !organisationId.equals(value.organisationId())
					|| !patientRegistrationId.equals(value.patientRegistrationId())
					|| !actorUserId.equals(value.doctorUserId())) {
				throw new CommunicationAccessDeniedException();
			}
		}
		catch (HttpClientErrorException.NotFound missing) { throw new ConversationNotFoundException(); }
		catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden denied) {
			throw new CommunicationAccessDeniedException();
		}
		catch (RestClientException unavailable) { throw new CommunicationContextUnavailableException(); }
	}
}
