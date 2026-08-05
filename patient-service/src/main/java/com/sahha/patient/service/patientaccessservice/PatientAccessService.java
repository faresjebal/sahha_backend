package com.sahha.patient.service.patientaccessservice;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.patient.client.organisation.OrganisationContextClient;
import com.sahha.patient.client.organisation.OrganisationContextResource;
import com.sahha.patient.exception.PatientAccessDeniedException;

@Service
public class PatientAccessService {

	private static final Set<String> ADMINISTRATIVE_ROLES =
			Set.of("ORGANIZATION_ADMIN", "RECEPTIONIST");

	private final OrganisationContextClient organisationContextClient;

	public PatientAccessService(
			OrganisationContextClient organisationContextClient) {
		this.organisationContextClient = organisationContextClient;
	}

	public void requireAdministrativeAccess(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = organisationContextClient.resolve(
				organisationId,
				accessToken);
		if (!organisationId.equals(context.organisationId())
				|| context.roles().stream().noneMatch(
						ADMINISTRATIVE_ROLES::contains)) {
			throw new PatientAccessDeniedException();
		}
		// The Organisation Service resolves the context for the authenticated JWT
		// subject, so actorUserId is deliberately not accepted from the client.
		if (actorUserId == null) {
			throw new PatientAccessDeniedException();
		}
	}
}
