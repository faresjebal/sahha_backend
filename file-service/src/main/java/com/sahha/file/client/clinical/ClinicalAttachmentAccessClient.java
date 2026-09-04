package com.sahha.file.client.clinical;

import java.util.Objects;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.sahha.file.config.FileSecurityProperties;
import com.sahha.file.exception.ClinicalContextUnavailableException;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.exception.FileResourceNotFoundException;

@Component
public class ClinicalAttachmentAccessClient {

	private final RestClient restClient;
	private final String accessTokenCookieName;

	public ClinicalAttachmentAccessClient(
			@Qualifier("fileClinicalRestClient") RestClient restClient,
			FileSecurityProperties securityProperties) {
		this.restClient = restClient;
		this.accessTokenCookieName = securityProperties.accessTokenCookieName();
	}

	public ClinicalAttachmentContextResource resolve(
			UUID consultationId,
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		Objects.requireNonNull(consultationId);
		Objects.requireNonNull(organisationId);
		Objects.requireNonNull(actorUserId);
		Objects.requireNonNull(accessToken);
		try {
			ClinicalAttachmentContextResource resource = restClient.get()
					.uri(
							"/api/v1/internal/clinical/consultations/{consultationId}/attachment-context",
							consultationId)
					.header(HttpHeaders.COOKIE,
							accessTokenCookieName + "=" + accessToken)
					.retrieve()
					.body(ClinicalAttachmentContextResource.class);
			if (!valid(resource, consultationId, organisationId, actorUserId)) {
				throw new ClinicalContextUnavailableException();
			}
			return resource;
		}
		catch (HttpClientErrorException.NotFound notFound) {
			throw new FileResourceNotFoundException();
		}
		catch (HttpClientErrorException.Unauthorized
				| HttpClientErrorException.Forbidden denied) {
			throw new FileAccessDeniedException();
		}
		catch (RestClientException unavailable) {
			throw new ClinicalContextUnavailableException();
		}
	}

	private static boolean valid(
			ClinicalAttachmentContextResource resource,
			UUID expectedConsultationId,
			UUID expectedOrganisationId,
			UUID expectedActorUserId) {
		return resource != null
				&& expectedConsultationId.equals(resource.consultationId())
				&& expectedOrganisationId.equals(resource.organisationId())
				&& resource.patientRegistrationId() != null
				&& resource.patientId() != null
				&& expectedActorUserId.equals(resource.doctorUserId())
				&& ("DRAFT".equals(resource.consultationStatus())
						|| "FINALIZED".equals(resource.consultationStatus()))
				&& resource.consultationVersion() >= 0;
	}
}
