package com.sahha.auth.service.refreshtokenservice;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.auth.entity.RefreshToken;
import com.sahha.auth.entity.UserSession;
import com.sahha.auth.repository.RefreshTokenRepository;
import com.sahha.auth.security.SecureTokenGenerator;
import com.sahha.auth.security.TokenHashingService;

@Service
public class RefreshTokenService {

	private final RefreshTokenRepository tokenRepository;
	private final SecureTokenGenerator tokenGenerator;
	private final TokenHashingService tokenHashingService;

	public RefreshTokenService(
			RefreshTokenRepository tokenRepository,
			SecureTokenGenerator tokenGenerator,
			TokenHashingService tokenHashingService) {
		this.tokenRepository = tokenRepository;
		this.tokenGenerator = tokenGenerator;
		this.tokenHashingService = tokenHashingService;
	}

	public IssuedRefreshToken issueInitial(
			UserSession session,
			Instant issuedAt,
			Instant expiresAt,
			String createdIp,
			String createdUserAgent) {
		String rawToken = tokenGenerator.generate();
		RefreshToken token = RefreshToken.issueInitial(
				session,
				tokenHashingService.hash(rawToken),
				issuedAt,
				expiresAt,
				createdIp,
				createdUserAgent);
		tokenRepository.saveAndFlush(token);
		return issued(token, rawToken);
	}

	public IssuedRefreshToken rotate(
			RefreshToken currentToken,
			UserSession session,
			Instant rotatedAt,
			Instant replacementExpiresAt,
			String createdIp,
			String createdUserAgent) {
		RefreshToken requiredCurrent = Objects.requireNonNull(
				currentToken,
				"currentToken must not be null");
		UserSession requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");

		requiredCurrent.consumeForRotation(rotatedAt);
		tokenRepository.saveAndFlush(requiredCurrent);

		String rawReplacement = tokenGenerator.generate();
		RefreshToken replacement = RefreshToken.issueReplacement(
				requiredSession,
				requiredCurrent,
				tokenHashingService.hash(rawReplacement),
				rotatedAt,
				replacementExpiresAt,
				createdIp,
				createdUserAgent);
		tokenRepository.saveAndFlush(replacement);

		requiredCurrent.linkReplacement(replacement);
		tokenRepository.saveAndFlush(requiredCurrent);
		return issued(replacement, rawReplacement);
	}

	public Optional<RefreshToken> findPresentedForUpdate(String rawToken) {
		try {
			return tokenRepository.findByTokenHashForUpdate(
					tokenHashingService.hash(rawToken));
		}
		catch (NullPointerException | IllegalArgumentException invalidToken) {
			return Optional.empty();
		}
	}

	public void revokeFamilyForUpdate(
			UUID sessionId,
			Instant revokedAt,
			String reason) {
		Objects.requireNonNull(sessionId, "sessionId must not be null");
		for (RefreshToken token :
				tokenRepository.findAllBySessionIdForUpdate(sessionId)) {
			if (token.getRevokedAt() == null) {
				token.revoke(revokedAt, reason);
			}
		}
	}

	private static IssuedRefreshToken issued(
			RefreshToken token,
			String rawToken) {
		return new IssuedRefreshToken(
				token.getId(),
				token.getSession().getId(),
				rawToken,
				token.getExpiresAt());
	}
}
