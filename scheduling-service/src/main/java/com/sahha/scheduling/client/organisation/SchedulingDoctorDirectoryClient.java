package com.sahha.scheduling.client.organisation;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.scheduling.config.SchedulingSecurityProperties;
import com.sahha.scheduling.exception.AppointmentDoctorNotFoundException;
import com.sahha.scheduling.exception.OrganisationContextUnavailableException;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;

@Component
public class SchedulingDoctorDirectoryClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public SchedulingDoctorDirectoryClient(
			@Qualifier("schedulingOrganisationContextRestClient")
					RestClient restClient,
			SchedulingSecurityProperties securityProperties) {
		this.restClient = restClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public SchedulingDoctorResource findActiveDoctor(
			UUID organisationId,
			UUID doctorUserId,
			String accessToken) {
		try {
			SchedulingDoctorResource resource = restClient.get()
					.uri(
							"/api/v1/organisations/{organisationId}/scheduling-doctors/{doctorUserId}",
							organisationId,
							doctorUserId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(SchedulingDoctorResource.class);
			if (resource == null || resource.membershipId() == null
					|| !organisationId.equals(resource.organisationId())
					|| !doctorUserId.equals(resource.userId())) {
				throw new OrganisationContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new AppointmentDoctorNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new SchedulingAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new OrganisationContextUnavailableException();
		}
	}

	public SchedulingDoctorResource findPatientVisibleActiveDoctor(
			UUID organisationId,
			UUID doctorUserId,
			String accessToken) {
		try {
			SchedulingDoctorResource resource = restClient.get()
					.uri(
							"/api/v1/organisations/{organisationId}/patient-doctors/{doctorUserId}",
							organisationId,
							doctorUserId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(SchedulingDoctorResource.class);
			if (resource == null || resource.membershipId() == null
					|| !organisationId.equals(resource.organisationId())
					|| !doctorUserId.equals(resource.userId())) {
				throw new OrganisationContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new AppointmentDoctorNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new SchedulingAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new OrganisationContextUnavailableException();
		}
	}
}
