package com.sahha.organisation.service.staffinvitationservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.organisation.client.auth.AuthAccountDirectoryClient;
import com.sahha.organisation.client.auth.AuthAccountResource;
import com.sahha.organisation.dto.response.StaffInvitationPageResponse;
import com.sahha.organisation.dto.response.StaffInvitationResponse;
import com.sahha.organisation.exception.AccountNotEligibleException;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;

@Service
public class StaffInvitationIdentityService {

	private final AuthAccountDirectoryClient accountDirectoryClient;
	private final StaffInvitationService invitationService;

	public StaffInvitationIdentityService(
			AuthAccountDirectoryClient accountDirectoryClient,
			StaffInvitationService invitationService) {
		this.accountDirectoryClient = accountDirectoryClient;
		this.invitationService = invitationService;
	}

	public StaffInvitationPageResponse listMine(
			UUID actorUserId,
			String accessToken,
			int page,
			int size) {
		return invitationService.listMine(
				currentEligibleAccount(actorUserId, accessToken),
				page,
				size);
	}

	public StaffInvitationResponse accept(
			UUID invitationId,
			long version,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		return invitationService.accept(
				invitationId,
				version,
				currentEligibleAccount(actorUserId, accessToken),
				requestId);
	}

	public StaffInvitationResponse reject(
			UUID invitationId,
			long version,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		return invitationService.reject(
				invitationId,
				version,
				currentEligibleAccount(actorUserId, accessToken),
				requestId);
	}

	private AuthAccountResource currentEligibleAccount(
			UUID actorUserId,
			String accessToken) {
		AuthAccountResource account = accountDirectoryClient.currentAccount(
				accessToken);
		if (!actorUserId.equals(account.id())) {
			throw new OrganisationAccessDeniedException();
		}
		if (!account.eligibleForMembership()) {
			throw new AccountNotEligibleException();
		}
		return account;
	}
}
