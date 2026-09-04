package com.sahha.scheduling.client.patient;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.scheduling.config.SchedulingSecurityProperties;
import com.sahha.scheduling.exception.AppointmentPatientNotFoundException;
import com.sahha.scheduling.exception.PatientRegistryUnavailableException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;

@Component
public class PatientRegistryClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public PatientRegistryClient(
			@Qualifier("schedulingPatientRegistryRestClient") RestClient restClient,
			SchedulingSecurityProperties securityProperties) {
		this.restClient = restClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public PatientRegistrationResource findActiveRegistration(
			UUID organisationId,
			UUID registrationId,
			String accessToken) {
		try {
			PatientRegistrationResource resource = restClient.get()
					.uri("/api/v1/patients/{registrationId}", registrationId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(PatientRegistrationResource.class);
			if (resource == null
					|| !registrationId.equals(resource.registrationId())
					|| !organisationId.equals(resource.organisationId())
					|| resource.patientId() == null
					|| !"ACTIVE".equals(resource.registrationStatus())) {
				throw new AppointmentPatientNotFoundException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new AppointmentPatientNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new SchedulingAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new PatientRegistryUnavailableException();
		}
	}

	public PatientSchedulingContextResource findOwnActiveRegistration(
			UUID registrationId,
			UUID actorUserId,
			String accessToken) {
		try {
			PatientSchedulingContextResource resource = restClient.get()
					.uri(
							"/api/v1/patients/me/registrations/{registrationId}/scheduling-context",
							registrationId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(PatientSchedulingContextResource.class);
			if (resource == null
					|| !registrationId.equals(resource.registrationId())
					|| !actorUserId.equals(resource.authUserId())
					|| resource.patientId() == null
					|| resource.organisationId() == null
					|| !"ACTIVE".equals(resource.registrationStatus())) {
				throw new AppointmentPatientNotFoundException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new AppointmentPatientNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new SchedulingAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new PatientRegistryUnavailableException();
		}
	}
}
