package com.sahha.auth.service.refreshtokenservice;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.RefreshToken;
import com.sahha.auth.entity.SessionStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.exception.InvalidRefreshTokenException;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.repository.UserSessionRepository;
import com.sahha.auth.service.usersessionservice.UserSessionCacheService;
import com.sahha.auth.service.usersessionservice.IssuedSessionCredentials;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class RefreshTokenRotationService {

	private static final String REUSE_REASON = "REFRESH_TOKEN_REUSE";
	private static final String SESSION_EXPIRED_REASON = "SESSION_EXPIRED";
	private static final String ACCOUNT_INELIGIBLE_REASON = "ACCOUNT_INELIGIBLE";
	private static final String CREDENTIALS_CHANGED_REASON =
			"CREDENTIAL_VERSION_CHANGED";
	private static final String TOKEN_INVALIDATED_REASON =
			"REFRESH_TOKEN_INVALIDATED";

	private final RefreshTokenService refreshTokenService;
	private final UserSessionRepository sessionRepository;
	private final UserAccountRepository userRepository;
	private final AuthSecurityProperties securityProperties;
	private final UserSessionCacheService sessionCacheService;
	private final SecurityEventRecorder securityEventRecorder;

	public RefreshTokenRotationService(
			RefreshTokenService refreshTokenService,
			UserSessionRepository sessionRepository,
			UserAccountRepository userRepository,
			AuthSecurityProperties securityProperties,
			UserSessionCacheService sessionCacheService,
			SecurityEventRecorder securityEventRecorder) {
		this.refreshTokenService = refreshTokenService;
		this.sessionRepository = sessionRepository;
		this.userRepository = userRepository;
		this.securityProperties = securityProperties;
		this.sessionCacheService = sessionCacheService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public IssuedSessionCredentials rotate(
			String rawRefreshToken,
			String ipAddress,
			String userAgent,
			Instant rotatedAt) {
		Instant requiredRotatedAt = Objects.requireNonNull(
				rotatedAt,
				"rotatedAt must not be null");
		RefreshToken currentToken = refreshTokenService
				.findPresentedForUpdate(rawRefreshToken)
				.orElse(null);
		if (currentToken == null) {
			recordDenied(
					null,
					null,
					ipAddress,
					userAgent,
					requiredRotatedAt,
					"INVALID_REFRESH_CREDENTIAL");
			throw new InvalidRefreshTokenException();
		}
		UserSession session = sessionRepository
				.findByIdForUpdate(currentToken.getSession().getId())
				.orElse(null);
		if (session == null) {
			recordDenied(
					currentToken.getUser().getId(),
					currentToken.getSession().getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					"SESSION_NOT_FOUND");
			throw new InvalidRefreshTokenException();
		}
		UserAccount user = userRepository
				.findByIdForUpdate(currentToken.getUser().getId())
				.orElse(null);
		if (user == null) {
			recordDenied(
					null,
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					"ACCOUNT_NOT_FOUND");
			throw new InvalidRefreshTokenException();
		}

		if (requiredRotatedAt.isBefore(session.getCreatedAt())
				|| requiredRotatedAt.isBefore(currentToken.getCreatedAt())) {
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					"INVALID_REFRESH_TIME");
			throw new InvalidRefreshTokenException();
		}
		if (session.getStatus() == SessionStatus.ACTIVE
				&& currentToken.getUsedAt() != null) {
			session.markCompromised(requiredRotatedAt, REUSE_REASON);
			refreshTokenService.revokeFamilyForUpdate(
					session.getId(),
					requiredRotatedAt,
					REUSE_REASON);
			cachePersistedSession(session, requiredRotatedAt);
			securityEventRecorder.record(
					SecurityEventType.REFRESH_TOKEN_REUSE_DETECTED,
					SecurityEventResult.HIGH_RISK,
					REUSE_REASON,
					SecurityEventContext.authentication(
							user.getId(),
							null,
							session.getId(),
							ipAddress,
							userAgent),
					requiredRotatedAt);
			throw new InvalidRefreshTokenException();
		}
		if (session.getStatus() != SessionStatus.ACTIVE) {
			sessionCacheService.cacheAfterCommit(
					session,
					requiredRotatedAt);
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					"SESSION_INACTIVE");
			throw new InvalidRefreshTokenException();
		}
		if (!user.canAuthenticate()) {
			revokeSession(
					session,
					requiredRotatedAt,
					ACCOUNT_INELIGIBLE_REASON);
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					ACCOUNT_INELIGIBLE_REASON);
			throw new InvalidRefreshTokenException();
		}
		if (session.getCredentialVersionAtCreation()
				!= user.getCredentialVersion()) {
			revokeSession(
					session,
					requiredRotatedAt,
					CREDENTIALS_CHANGED_REASON);
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					CREDENTIALS_CHANGED_REASON);
			throw new InvalidRefreshTokenException();
		}
		if (!requiredRotatedAt.isBefore(session.getIdleExpiresAt())
				|| !requiredRotatedAt.isBefore(
						session.getAbsoluteExpiresAt())) {
			session.expire(requiredRotatedAt);
			refreshTokenService.revokeFamilyForUpdate(
					session.getId(),
					requiredRotatedAt,
					SESSION_EXPIRED_REASON);
			cachePersistedSession(session, requiredRotatedAt);
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					SESSION_EXPIRED_REASON);
			throw new InvalidRefreshTokenException();
		}
		if (!currentToken.isActiveAt(requiredRotatedAt)) {
			revokeSession(
					session,
					requiredRotatedAt,
					TOKEN_INVALIDATED_REASON);
			recordDenied(
					user.getId(),
					session.getId(),
					ipAddress,
					userAgent,
					requiredRotatedAt,
					TOKEN_INVALIDATED_REASON);
			throw new InvalidRefreshTokenException();
		}

		Instant proposedIdleExpiration = requiredRotatedAt.plus(
				securityProperties.sessionIdleLifetime());
		Instant nextIdleExpiration =
				proposedIdleExpiration.isAfter(session.getAbsoluteExpiresAt())
						? session.getAbsoluteExpiresAt()
						: proposedIdleExpiration;
		session.recordActivity(
				requiredRotatedAt,
				nextIdleExpiration,
				ipAddress);
		IssuedRefreshToken replacement = refreshTokenService.rotate(
				currentToken,
				session,
				requiredRotatedAt,
				nextIdleExpiration,
				ipAddress,
				userAgent);
		cachePersistedSession(session, requiredRotatedAt);
		securityEventRecorder.record(
				SecurityEventType.REFRESH_SUCCEEDED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.authentication(
						user.getId(),
						null,
						session.getId(),
						ipAddress,
						userAgent),
				requiredRotatedAt);
		return IssuedSessionCredentials.from(session, replacement);
	}

	private void recordDenied(
			java.util.UUID userId,
			java.util.UUID sessionId,
			String ipAddress,
			String userAgent,
			Instant occurredAt,
			String reasonCode) {
		securityEventRecorder.record(
				SecurityEventType.REFRESH_DENIED,
				SecurityEventResult.DENIED,
				reasonCode,
				SecurityEventContext.authentication(
						userId,
						null,
						sessionId,
						ipAddress,
						userAgent),
				occurredAt);
	}

	private void revokeSession(
			UserSession session,
			Instant revokedAt,
			String reason) {
		session.revoke(revokedAt, null, reason);
		refreshTokenService.revokeFamilyForUpdate(
				session.getId(),
				revokedAt,
				reason);
		cachePersistedSession(session, revokedAt);
	}

	private void cachePersistedSession(
			UserSession session,
			Instant observedAt) {
		sessionRepository.saveAndFlush(session);
		sessionCacheService.cacheAfterCommit(session, observedAt);
	}
}
