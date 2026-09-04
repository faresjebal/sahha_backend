package com.sahha.clinical.client.scheduling;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.clinical.config.ClinicalSecurityProperties;
import com.sahha.clinical.exception.ClinicalAccessDeniedException;
import com.sahha.clinical.exception.ClinicalAppointmentNotFoundException;
import com.sahha.clinical.exception.SchedulingContextUnavailableException;

@Component
public class SchedulingClinicalContextClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;
	private final String csrfTokenCookieName;
	private final String csrfHeaderName;

	public SchedulingClinicalContextClient(
			@Qualifier("clinicalSchedulingRestClient") RestClient restClient,
			ClinicalSecurityProperties securityProperties) {
		this.restClient = restClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
		this.csrfTokenCookieName = securityProperties.csrfTokenName();
		this.csrfHeaderName = securityProperties.csrfHeaderName();
	}

	public ClinicalAppointmentCompletionResource recoverCompletion(
			UUID appointmentId,
			ClinicalCompletionRecoveryCommand command,
			String accessToken,
			String csrfToken) {
		Objects.requireNonNull(appointmentId);
		Objects.requireNonNull(command);
		Objects.requireNonNull(accessToken);
		Objects.requireNonNull(csrfToken);
		try {
			ClinicalAppointmentCompletionResource resource = restClient.post()
					.uri(
							"/api/v1/internal/clinical/appointments/{appointmentId}/completion-recovery",
							appointmentId)
					.header(
							HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken + "; "
									+ csrfTokenCookieName + "=" + csrfToken)
					.header(csrfHeaderName, csrfToken)
					.body(command)
					.retrieve()
					.body(ClinicalAppointmentCompletionResource.class);
			if (!valid(resource, appointmentId, command.eventId())) {
				throw new SchedulingContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new ClinicalAppointmentNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new ClinicalAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new SchedulingContextUnavailableException();
		}
	}

	public ClinicalAppointmentContextResource resolve(
			UUID appointmentId,
			String accessToken) {
		Objects.requireNonNull(appointmentId);
		Objects.requireNonNull(accessToken);
		try {
			ClinicalAppointmentContextResource resource = restClient.get()
					.uri("/api/v1/internal/clinical/appointments/{appointmentId}",
							appointmentId)
					.header(HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(ClinicalAppointmentContextResource.class);
			if (!valid(resource, appointmentId)) {
				throw new SchedulingContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new ClinicalAppointmentNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new ClinicalAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new SchedulingContextUnavailableException();
		}
	}

	public ClinicalPatientAccessContextResource resolvePatientAccess(
			UUID patientRegistrationId,
			String accessToken) {
		Objects.requireNonNull(patientRegistrationId);
		Objects.requireNonNull(accessToken);
		try {
			ClinicalPatientAccessContextResource resource = restClient.get()
					.uri(
							"/api/v1/internal/clinical/patients/{patientRegistrationId}/access-context",
							patientRegistrationId)
					.header(HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(ClinicalPatientAccessContextResource.class);
			if (!valid(resource, patientRegistrationId)) {
				throw new SchedulingContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new ClinicalAppointmentNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new ClinicalAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new SchedulingContextUnavailableException();
		}
	}

	private static boolean valid(
			ClinicalAppointmentContextResource resource,
			UUID expectedAppointmentId) {
		return resource != null
				&& expectedAppointmentId.equals(resource.appointmentId())
				&& resource.organisationId() != null
				&& resource.patientRegistrationId() != null
				&& resource.patientId() != null
				&& resource.doctorUserId() != null
				&& resource.doctorMembershipId() != null
				&& resource.status() != null
				&& resource.startsAt() != null
				&& resource.endsAt() != null
				&& resource.version() >= 0;
	}

	private static boolean valid(
			ClinicalPatientAccessContextResource resource,
			UUID expectedPatientRegistrationId) {
		return resource != null
				&& resource.appointmentId() != null
				&& resource.organisationId() != null
				&& expectedPatientRegistrationId.equals(
						resource.patientRegistrationId())
				&& resource.patientId() != null
				&& resource.doctorUserId() != null
				&& resource.doctorMembershipId() != null
				&& ("CONFIRMED".equals(resource.status())
						|| "CHECKED_IN".equals(resource.status())
						|| "IN_PROGRESS".equals(resource.status()))
				&& resource.appointmentVersion() >= 0;
	}

	private static boolean valid(
			ClinicalAppointmentCompletionResource resource,
			UUID expectedAppointmentId,
			UUID expectedEventId) {
		if (resource == null
				|| !expectedAppointmentId.equals(resource.appointmentId())
				|| !expectedEventId.equals(resource.eventId())) {
			return false;
		}
		if ("CONFLICT".equals(resource.outcome())) {
			return resource.conflictCode() != null
					&& !resource.conflictCode().isBlank();
		}
		return ("APPLIED".equals(resource.outcome())
				|| "IDEMPOTENT".equals(resource.outcome()))
				&& "COMPLETED".equals(resource.appointmentStatus())
				&& resource.appointmentVersion() != null
				&& resource.appointmentVersion() >= 0
				&& resource.conflictCode() == null;
	}
}
