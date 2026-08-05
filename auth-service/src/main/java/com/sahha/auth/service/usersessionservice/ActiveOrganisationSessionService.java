package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.client.organisation.OrganisationContextResource;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.exception.InvalidOrganisationContextException;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.service.accesstokenservice.AccessTokenService;
import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;
import com.sahha.auth.service.userplatformroleservice.UserPlatformRoleService;

@Service
public class ActiveOrganisationSessionService {

	private final UserSessionRepository sessionRepository;
	private final UserSessionCacheService sessionCacheService;
	private final UserPlatformRoleService platformRoleService;
	private final AccessTokenService accessTokenService;
	private final SecurityEventRecorder securityEventRecorder;

	public ActiveOrganisationSessionService(
			UserSessionRepository sessionRepository,
			UserSessionCacheService sessionCacheService,
			UserPlatformRoleService platformRoleService,
			AccessTokenService accessTokenService,
			SecurityEventRecorder securityEventRecorder) {
		this.sessionRepository = sessionRepository;
		this.sessionCacheService = sessionCacheService;
		this.platformRoleService = platformRoleService;
		this.accessTokenService = accessTokenService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional
	public IssuedActiveOrganisationSession select(
			UUID userId,
			UUID sessionId,
			OrganisationContextResource context,
			Instant selectedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		OrganisationContextResource requiredContext = Objects.requireNonNull(
				context,
				"context must not be null");
		Instant requiredSelectedAt = Objects.requireNonNull(
				selectedAt,
				"selectedAt must not be null");
		UserSession session = sessionRepository.findByIdForUpdate(sessionId)
				.filter(candidate -> userId.equals(candidate.getUser().getId()))
				.filter(candidate -> candidate.isActiveAt(
						requiredSelectedAt,
						candidate.getUser().getCredentialVersion()))
				.orElseThrow(InvalidOrganisationContextException::new);
		List<String> organisationRoles = requiredContext.roles()
				.stream()
				.sorted()
				.toList();
		session.selectActiveOrganisation(
				requiredContext.organisationId(),
				organisationRoles);
		sessionRepository.saveAndFlush(session);
		List<String> platformRoles = platformRoleService.findActiveRoleCodes(
				userId);
		IssuedAccessToken accessToken = accessTokenService.issue(
				session,
				platformRoles,
				requiredSelectedAt);
		sessionCacheService.cacheAfterCommit(session, requiredSelectedAt);
		securityEventRecorder.record(
				SecurityEventType.ACTIVE_ORGANISATION_SELECTED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.organisationSelection(
						userId,
						sessionId,
						requiredContext.organisationId()),
				requiredSelectedAt);
		return new IssuedActiveOrganisationSession(
				userId,
				sessionId,
				accessToken,
				platformRoles,
				requiredContext);
	}
}
