package com.sahha.scheduling.service.availabilityservice;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.scheduling.client.organisation.OrganisationContextClient;
import com.sahha.scheduling.client.organisation.OrganisationContextResource;
import com.sahha.scheduling.exception.SchedulingAccessDeniedException;

@Service
public class SchedulingAccessService {

	private static final Set<String> SLOT_READ_ROLES = Set.of(
			"DOCTOR", "RECEPTIONIST", "ORGANIZATION_ADMIN");
	private final OrganisationContextClient organisationContextClient;

	public SchedulingAccessService(
			OrganisationContextClient organisationContextClient) {
		this.organisationContextClient = organisationContextClient;
	}

	public OrganisationContextResource requireDoctor(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = requireContext(
				organisationId, actorUserId, accessToken);
		if (!context.roles().contains("DOCTOR")) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}

	public OrganisationContextResource requireSlotRead(
			UUID organisationId,
			UUID actorUserId,
			UUID doctorUserId,
			String accessToken) {
		OrganisationContextResource context = requireContext(
				organisationId, actorUserId, accessToken);
		if (context.roles().stream().noneMatch(SLOT_READ_ROLES::contains)) {
			throw new SchedulingAccessDeniedException();
		}
		boolean operational = context.roles().contains("RECEPTIONIST")
				|| context.roles().contains("ORGANIZATION_ADMIN");
		if (!operational && !actorUserId.equals(doctorUserId)) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}

	public OrganisationContextResource requireDirectoryRead(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = requireContext(
				organisationId, actorUserId, accessToken);
		if (context.roles().stream().noneMatch(SLOT_READ_ROLES::contains)) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}

	public OrganisationContextResource requireBooking(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = requireContext(
				organisationId, actorUserId, accessToken);
		boolean permitted = context.roles().contains("RECEPTIONIST")
				|| context.roles().contains("ORGANIZATION_ADMIN");
		if (!permitted) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}

	public OrganisationContextResource requireAppointmentAccess(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		OrganisationContextResource context = requireContext(
				organisationId, actorUserId, accessToken);
		if (context.roles().stream().noneMatch(SLOT_READ_ROLES::contains)) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}

	private OrganisationContextResource requireContext(
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		if (organisationId == null || actorUserId == null
				|| accessToken == null || accessToken.isBlank()) {
			throw new SchedulingAccessDeniedException();
		}
		OrganisationContextResource context = organisationContextClient.resolve(
				organisationId, accessToken);
		if (!organisationId.equals(context.organisationId())) {
			throw new SchedulingAccessDeniedException();
		}
		return context;
	}
}
