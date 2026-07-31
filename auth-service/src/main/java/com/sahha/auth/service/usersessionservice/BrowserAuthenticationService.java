package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.exception.InvalidAuthenticationException;
import com.sahha.auth.exception.InvalidRefreshTokenException;
import com.sahha.auth.service.accesstokenservice.AccessTokenService;
import com.sahha.auth.service.accesstokenservice.IssuedAccessToken;
import com.sahha.auth.service.refreshtokenservice.RefreshTokenRotationService;
import com.sahha.auth.service.userplatformroleservice.UserPlatformRoleService;

@Service
public class BrowserAuthenticationService {

	private final UserSessionService sessionService;
	private final RefreshTokenRotationService rotationService;
	private final UserPlatformRoleService platformRoleService;
	private final AccessTokenService accessTokenService;

	public BrowserAuthenticationService(
			UserSessionService sessionService,
			RefreshTokenRotationService rotationService,
			UserPlatformRoleService platformRoleService,
			AccessTokenService accessTokenService) {
		this.sessionService = sessionService;
		this.rotationService = rotationService;
		this.platformRoleService = platformRoleService;
		this.accessTokenService = accessTokenService;
	}

	@Transactional(noRollbackFor = InvalidAuthenticationException.class)
	public IssuedBrowserSession login(
			String email,
			CharSequence rawPassword,
			String rawDeviceId,
			String deviceName,
			String userAgent,
			String ipAddress,
			Instant authenticatedAt) {
		IssuedSessionCredentials session = sessionService.login(
				email,
				rawPassword,
				rawDeviceId,
				deviceName,
				userAgent,
				ipAddress,
				authenticatedAt)
				.orElseThrow(InvalidAuthenticationException::new);
		return issueBrowserSession(session, authenticatedAt);
	}

	@Transactional(noRollbackFor = InvalidRefreshTokenException.class)
	public IssuedBrowserSession refresh(
			String rawRefreshToken,
			String ipAddress,
			String userAgent,
			Instant refreshedAt) {
		IssuedSessionCredentials session = rotationService.rotate(
				rawRefreshToken,
				ipAddress,
				userAgent,
				refreshedAt);
		return issueBrowserSession(session, refreshedAt);
	}

	public boolean logoutCurrent(
			UUID userId,
			UUID sessionId,
			Instant revokedAt) {
		return sessionService.logoutCurrent(userId, sessionId, revokedAt);
	}

	public int logoutEverywhere(UUID userId, Instant revokedAt) {
		return sessionService.logoutEverywhere(userId, revokedAt);
	}

	private IssuedBrowserSession issueBrowserSession(
			IssuedSessionCredentials session,
			Instant issuedAt) {
		Objects.requireNonNull(session, "session must not be null");
		List<String> roles = platformRoleService.findActiveRoleCodes(
				session.getUserId());
		IssuedAccessToken accessToken = accessTokenService.issue(
				session,
				roles,
				issuedAt);
		return new IssuedBrowserSession(session, accessToken, roles);
	}
}
