package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.security.TokenHashingService;
import com.sahha.auth.service.refreshtokenservice.IssuedRefreshToken;
import com.sahha.auth.service.refreshtokenservice.RefreshTokenService;
import com.sahha.auth.service.useraccountservice.AccountAuthenticationService;
import com.sahha.auth.service.useraccountservice.AuthenticationResult;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class UserSessionService {

	private static final String USER_LOGOUT = "USER_LOGOUT";
	private static final String USER_GLOBAL_LOGOUT = "USER_GLOBAL_LOGOUT";
	private static final String USER_SESSION_REVOKED = "USER_SESSION_REVOKED";

	private final AccountAuthenticationService authenticationService;
	private final UserAccountRepository userRepository;
	private final UserSessionRepository sessionRepository;
	private final RefreshTokenService refreshTokenService;
	private final TokenHashingService tokenHashingService;
	private final AuthSecurityProperties securityProperties;
	private final UserSessionCacheService sessionCacheService;
	private final SecurityEventRecorder securityEventRecorder;

	public UserSessionService(
			AccountAuthenticationService authenticationService,
			UserAccountRepository userRepository,
			UserSessionRepository sessionRepository,
			RefreshTokenService refreshTokenService,
			TokenHashingService tokenHashingService,
			AuthSecurityProperties securityProperties,
			UserSessionCacheService sessionCacheService,
			SecurityEventRecorder securityEventRecorder) {
		this.authenticationService = authenticationService;
		this.userRepository = userRepository;
		this.sessionRepository = sessionRepository;
		this.refreshTokenService = refreshTokenService;
		this.tokenHashingService = tokenHashingService;
		this.securityProperties = securityProperties;
		this.sessionCacheService = sessionCacheService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional
	public Optional<IssuedSessionCredentials> login(
			String email,
			CharSequence rawPassword,
			String rawDeviceId,
			String deviceName,
			String userAgent,
			String ipAddress,
			Instant authenticatedAt) {
		Instant requiredAuthenticatedAt = Objects.requireNonNull(
				authenticatedAt,
				"authenticatedAt must not be null");
		String deviceIdHash = tokenHashingService.hash(rawDeviceId);

		AuthenticationResult authentication = authenticationService.authenticate(
				email,
				rawPassword,
				requiredAuthenticatedAt);
		if (!authentication.isAuthenticated()) {
			securityEventRecorder.record(
					SecurityEventType.LOGIN_DENIED,
					SecurityEventResult.DENIED,
					"INVALID_CREDENTIALS_OR_ACCOUNT_STATE",
					SecurityEventContext.authentication(
							null,
							email,
							null,
							ipAddress,
							userAgent),
					requiredAuthenticatedAt);
			return Optional.empty();
		}

		UUID userId = authentication.authenticatedUserId().orElseThrow();
		UserAccount user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalStateException(
						"authenticated account no longer exists"));
		Instant absoluteExpiresAt = requiredAuthenticatedAt.plus(
				securityProperties.sessionAbsoluteLifetime());
		Instant idleExpiresAt = requiredAuthenticatedAt.plus(
				securityProperties.sessionIdleLifetime());
		UserSession session = UserSession.open(
				user,
				null,
				deviceIdHash,
				deviceName,
				userAgent,
				ipAddress,
				requiredAuthenticatedAt,
				idleExpiresAt,
				absoluteExpiresAt);
		sessionRepository.saveAndFlush(session);

		IssuedRefreshToken refreshToken = refreshTokenService.issueInitial(
				session,
				requiredAuthenticatedAt,
				idleExpiresAt,
				ipAddress,
				userAgent);
		sessionCacheService.cacheAfterCommit(
				session,
				requiredAuthenticatedAt);
		securityEventRecorder.record(
				SecurityEventType.LOGIN_SUCCEEDED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.authentication(
						user.getId(),
						email,
						session.getId(),
						ipAddress,
						userAgent),
				requiredAuthenticatedAt);
		return Optional.of(IssuedSessionCredentials.from(session, refreshToken));
	}

	@Transactional
	public List<UserSessionSummary> listActiveSessions(
			UUID userId,
			UUID currentSessionId,
			Instant observedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(
				currentSessionId,
				"currentSessionId must not be null");
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		UserAccount user = userRepository.findById(userId).orElse(null);
		if (user == null) {
			return List.of();
		}
		List<UserSessionSummary> sessions = sessionRepository
				.findAllByUser_IdAndStatusOrderByCreatedAtDesc(
						userId,
						SessionStatus.ACTIVE)
				.stream()
				.filter(session -> session.isActiveAt(
						requiredObservedAt,
						user.getCredentialVersion()))
				.map(session -> UserSessionSummary.from(
						session,
						currentSessionId))
				.toList();
		securityEventRecorder.record(
				SecurityEventType.SESSION_LIST_VIEWED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.session(userId, currentSessionId),
				requiredObservedAt);
		return sessions;
	}

	@Transactional
	public boolean revokeOwnedSession(
			UUID userId,
			UUID sessionId,
			Instant revokedAt) {
		return revokeOwnedSession(
				userId,
				sessionId,
				revokedAt,
				USER_SESSION_REVOKED);
	}

	@Transactional
	public boolean logoutCurrent(
			UUID userId,
			UUID sessionId,
			Instant revokedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		Instant requiredRevokedAt = Objects.requireNonNull(
				revokedAt,
				"revokedAt must not be null");

		return revokeOwnedSession(
				userId,
				sessionId,
				requiredRevokedAt,
				USER_LOGOUT);
	}

	private boolean revokeOwnedSession(
			UUID userId,
			UUID sessionId,
			Instant revokedAt,
			String reason) {
		Optional<UserSession> sessionResult =
				sessionRepository.findByIdForUpdate(sessionId);
		if (sessionResult.isEmpty()
				|| !userId.equals(sessionResult.orElseThrow().getUser().getId())) {
			return false;
		}

		UserSession session = sessionResult.orElseThrow();
		if (session.getStatus() == SessionStatus.ACTIVE) {
			session.revoke(revokedAt, session.getUser(), reason);
		}
		refreshTokenService.revokeFamilyForUpdate(
				session.getId(),
				revokedAt,
				reason);
		sessionRepository.saveAndFlush(session);
		sessionCacheService.cacheAfterCommit(session, revokedAt);
		securityEventRecorder.record(
				SecurityEventType.SESSION_REVOKED,
				SecurityEventResult.SUCCESS,
				reason,
				SecurityEventContext.session(userId, sessionId),
				revokedAt);
		return true;
	}

	@Transactional
	public int logoutEverywhere(UUID userId, Instant revokedAt) {
		return revokeAllActiveSessions(
				userId,
				revokedAt,
				USER_GLOBAL_LOGOUT);
	}

	@Transactional
	public int revokeAllForSecurityChange(
			UUID userId,
			Instant revokedAt,
			String reason) {
		return revokeAllActiveSessions(userId, revokedAt, reason);
	}

	private int revokeAllActiveSessions(
			UUID userId,
			Instant revokedAt,
			String reason) {
		Objects.requireNonNull(userId, "userId must not be null");
		Instant requiredRevokedAt = Objects.requireNonNull(
				revokedAt,
				"revokedAt must not be null");
		UserAccount user = userRepository.findByIdForUpdate(userId)
				.orElse(null);
		if (user == null) {
			return 0;
		}

		var activeSessions =
				sessionRepository.findAllByUserIdAndStatusForUpdate(
						userId,
						SessionStatus.ACTIVE);
		for (UserSession session : activeSessions) {
			session.revoke(requiredRevokedAt, user, reason);
			refreshTokenService.revokeFamilyForUpdate(
					session.getId(),
					requiredRevokedAt,
					reason);
		}
		if (!activeSessions.isEmpty()) {
			sessionRepository.flush();
			for (UserSession session : activeSessions) {
				sessionCacheService.cacheAfterCommit(
						session,
						requiredRevokedAt);
			}
		}
		securityEventRecorder.record(
				SecurityEventType.ALL_SESSIONS_REVOKED,
				SecurityEventResult.SUCCESS,
				reason,
				SecurityEventContext.account(userId),
				requiredRevokedAt);
		return activeSessions.size();
	}
}
