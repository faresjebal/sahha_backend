package com.sahha.organisation.service.organisationmembershipservice;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.request.AssignOrganisationAdministratorRequest;
import com.sahha.organisation.dto.response.OrganisationMembershipResponse;
import com.sahha.organisation.exception.AccountNotEligibleException;

@Service
public class OrganisationAdministratorAssignmentService {

	private final AuthAccountDirectoryClient accountDirectoryClient;
	private final OrganisationMembershipService membershipService;

	public OrganisationAdministratorAssignmentService(
			AuthAccountDirectoryClient accountDirectoryClient,
			OrganisationMembershipService membershipService) {
		this.accountDirectoryClient = accountDirectoryClient;
		this.membershipService = membershipService;
	}

	public OrganisationMembershipResponse assign(
			UUID organisationId,
			AssignOrganisationAdministratorRequest request,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		Objects.requireNonNull(request, "request must not be null");
		AuthAccountResource account = accountDirectoryClient.findByEmail(
				request.email(),
				accessToken);
		if (!account.eligibleForMembership()) {
			throw new AccountNotEligibleException();
		}
		return membershipService.assignAdministrator(
				organisationId,
				account,
				actorUserId,
				requestId);
	}
}
