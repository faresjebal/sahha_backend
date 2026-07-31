package com.sahha.auth.service.verificationtokenservice;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.VerificationToken;
import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.exception.InvalidVerificationTokenException;
import com.sahha.auth.repository.VerificationTokenRepository;
import com.sahha.auth.security.SecureTokenGenerator;
import com.sahha.auth.security.TokenHashingService;

@Service
public class VerificationTokenService {

	private final VerificationTokenRepository repository;
	private final SecureTokenGenerator tokenGenerator;
	private final TokenHashingService tokenHashingService;
	private final AuthSecurityProperties securityProperties;

	public VerificationTokenService(
			VerificationTokenRepository repository,
			SecureTokenGenerator tokenGenerator,
			TokenHashingService tokenHashingService,
			AuthSecurityProperties securityProperties) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.tokenHashingService = tokenHashingService;
		this.securityProperties = securityProperties;
	}

	@Transactional
	public IssuedVerificationToken issue(
			UserAccount user,
			VerificationTokenPurpose purpose,
			Instant issuedAt) {
		UserAccount requiredUser = Objects.requireNonNull(
				user,
				"user must not be null");
		VerificationTokenPurpose requiredPurpose = Objects.requireNonNull(
				purpose,
				"purpose must not be null");
		Instant requiredIssuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		validateIssueRequest(requiredUser, requiredPurpose);

		List<VerificationToken> activeTokens =
				lockActiveTokens(requiredUser, requiredPurpose);
		return issueReplacing(
				requiredUser,
				requiredPurpose,
				requiredIssuedAt,
				activeTokens);
	}

	@Transactional
	public Optional<IssuedVerificationToken> issueIfCooldownElapsed(
			UserAccount user,
			VerificationTokenPurpose purpose,
			Instant issuedAt,
			Duration cooldown) {
		UserAccount requiredUser = Objects.requireNonNull(
				user,
				"user must not be null");
		VerificationTokenPurpose requiredPurpose = Objects.requireNonNull(
				purpose,
				"purpose must not be null");
		Instant requiredIssuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		Duration requiredCooldown = Objects.requireNonNull(
				cooldown,
				"cooldown must not be null");
		if (requiredCooldown.isZero() || requiredCooldown.isNegative()) {
			throw new IllegalArgumentException("cooldown must be positive");
		}
		validateIssueRequest(requiredUser, requiredPurpose);

		List<VerificationToken> activeTokens =
				lockActiveTokens(requiredUser, requiredPurpose);
		boolean coolingDown = activeTokens.stream()
				.anyMatch(token -> requiredIssuedAt.isBefore(
						token.getCreatedAt().plus(requiredCooldown)));
		if (coolingDown) {
			return Optional.empty();
		}
		return Optional.of(issueReplacing(
				requiredUser,
				requiredPurpose,
				requiredIssuedAt,
				activeTokens));
	}

	private List<VerificationToken> lockActiveTokens(
			UserAccount user,
			VerificationTokenPurpose purpose) {
		return repository.findActiveByUserAndPurposeForUpdate(
				user.getId(),
				purpose);
	}

	private IssuedVerificationToken issueReplacing(
			UserAccount user,
			VerificationTokenPurpose purpose,
			Instant issuedAt,
			List<VerificationToken> activeTokens) {
		for (VerificationToken activeToken : activeTokens) {
			activeToken.revoke(issuedAt, "REPLACED");
		}
		if (!activeTokens.isEmpty()) {
			repository.flush();
		}

		String rawToken = tokenGenerator.generate();
		Instant expiresAt = issuedAt.plus(lifetime(purpose));
		VerificationToken token = VerificationToken.issue(
				user,
				purpose,
				tokenHashingService.hash(rawToken),
				issuedAt,
				expiresAt);
		repository.saveAndFlush(token);

		return new IssuedVerificationToken(
				token.getId(),
				rawToken,
				purpose,
				expiresAt);
	}

	@Transactional
	public VerificationToken consume(
			String rawToken,
			VerificationTokenPurpose expectedPurpose,
			Instant consumedAt) {
		Objects.requireNonNull(expectedPurpose, "expectedPurpose must not be null");
		Objects.requireNonNull(consumedAt, "consumedAt must not be null");

		String tokenHash;
		try {
			tokenHash = tokenHashingService.hash(rawToken);
		}
		catch (RuntimeException invalidRawToken) {
			throw new InvalidVerificationTokenException();
		}

		VerificationToken token = repository
				.findByTokenHashForUpdate(tokenHash)
				.orElseThrow(InvalidVerificationTokenException::new);
		if (token.getPurpose() != expectedPurpose
				|| !token.isActiveAt(consumedAt)) {
			throw new InvalidVerificationTokenException();
		}
		token.consume(consumedAt);
		return token;
	}

	private void validateIssueRequest(
			UserAccount user,
			VerificationTokenPurpose purpose) {
		if (user.getStatus() == AccountStatus.DISABLED) {
			throw new IllegalStateException(
					"verification tokens cannot be issued for disabled accounts");
		}
		if (purpose == VerificationTokenPurpose.EMAIL_VERIFICATION
				&& (user.getStatus() != AccountStatus.PENDING_VERIFICATION
					|| user.getEmailVerifiedAt() != null)) {
			throw new IllegalStateException(
					"email verification requires a pending unverified account");
		}
	}

	private Duration lifetime(VerificationTokenPurpose purpose) {
		return switch (purpose) {
			case EMAIL_VERIFICATION ->
				securityProperties.emailVerificationLifetime();
			case PASSWORD_RESET -> securityProperties.passwordResetLifetime();
			case EMAIL_CHANGE -> securityProperties.emailChangeLifetime();
		};
	}
}
