package com.sahha.auth.service.useraccountservice;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.entity.VerificationToken;
import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.security.PasswordPolicy;
import com.sahha.auth.service.usersessionservice.UserSessionService;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;
import com.sahha.auth.service.verificationtokenservice.VerificationTokenService;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class AccountVerificationService {

	private final UserAccountRepository userRepository;
	private final VerificationTokenService verificationTokenService;
	private final PasswordPolicy passwordPolicy;
	private final PasswordEncoder passwordEncoder;
	private final EmailNormalizer emailNormalizer;
	private final AuthSecurityProperties securityProperties;
	private final UserSessionService userSessionService;
	private final SecurityEventRecorder securityEventRecorder;

	public AccountVerificationService(
			UserAccountRepository userRepository,
			VerificationTokenService verificationTokenService,
			PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder,
			EmailNormalizer emailNormalizer,
			AuthSecurityProperties securityProperties,
			UserSessionService userSessionService,
			SecurityEventRecorder securityEventRecorder) {
		this.userRepository = userRepository;
		this.verificationTokenService = verificationTokenService;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.emailNormalizer = emailNormalizer;
		this.securityProperties = securityProperties;
		this.userSessionService = userSessionService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional
	public UUID verifyEmail(String rawToken, Instant verifiedAt) {
		VerificationToken token = verificationTokenService.consume(
				rawToken,
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				verifiedAt);
		token.getUser().verifyEmail(verifiedAt);
		securityEventRecorder.record(
				SecurityEventType.EMAIL_VERIFIED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.account(token.getUser().getId()),
				verifiedAt);
		return token.getUser().getId();
	}

	@Transactional
	public UUID resetPassword(
			String rawToken,
			CharSequence newRawPassword,
			Instant changedAt) {
		passwordPolicy.validate(newRawPassword);
		VerificationToken token = verificationTokenService.consume(
				rawToken,
				VerificationTokenPurpose.PASSWORD_RESET,
				changedAt);
		token.getUser().changePassword(
				passwordEncoder.encode(newRawPassword),
				changedAt);
		userSessionService.revokeAllForSecurityChange(
				token.getUser().getId(),
				changedAt,
				"PASSWORD_RESET");
		securityEventRecorder.record(
				SecurityEventType.PASSWORD_RESET_COMPLETED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.account(token.getUser().getId()),
				changedAt);
		return token.getUser().getId();
	}

	@Transactional
	public IssuedVerificationToken issuePasswordReset(
			UUID userId,
			Instant issuedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		UserAccount user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("unknown user"));
		return verificationTokenService.issue(
				user,
				VerificationTokenPurpose.PASSWORD_RESET,
				issuedAt);
	}

	@Transactional
	public IssuedVerificationToken resendEmailVerification(
			UUID userId,
			Instant issuedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		UserAccount user = userRepository.findByIdForUpdate(userId)
				.orElseThrow(() -> new IllegalArgumentException("unknown user"));
		return verificationTokenService.issue(
				user,
				VerificationTokenPurpose.EMAIL_VERIFICATION,
				issuedAt);
	}

	@Transactional
	public Optional<AccountTokenDelivery> requestEmailVerification(
			String email,
			Instant issuedAt) {
		Instant requiredIssuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		Optional<UserAccount> accountResult = findByEmailForUpdate(email);
		if (accountResult.isEmpty()) {
			return Optional.empty();
		}

		UserAccount account = accountResult.orElseThrow();
		if (account.getStatus() != AccountStatus.PENDING_VERIFICATION
				|| account.getEmailVerifiedAt() != null) {
			return Optional.empty();
		}
		return verificationTokenService.issueIfCooldownElapsed(
						account,
						VerificationTokenPurpose.EMAIL_VERIFICATION,
						requiredIssuedAt,
						securityProperties.emailVerificationRequestCooldown())
				.map(token -> {
					securityEventRecorder.record(
							SecurityEventType.EMAIL_VERIFICATION_REQUESTED,
							SecurityEventResult.SUCCESS,
							null,
							SecurityEventContext.account(account.getId()),
							requiredIssuedAt);
					return delivery(account, token);
				});
	}

	@Transactional
	public Optional<AccountTokenDelivery> requestPasswordReset(
			String email,
			Instant issuedAt) {
		Instant requiredIssuedAt = Objects.requireNonNull(
				issuedAt,
				"issuedAt must not be null");
		Optional<UserAccount> accountResult = findByEmailForUpdate(email);
		if (accountResult.isEmpty()) {
			return Optional.empty();
		}

		UserAccount account = accountResult.orElseThrow();
		boolean eligibleStatus = account.getStatus() == AccountStatus.ACTIVE
				|| account.getStatus() == AccountStatus.LOCKED;
		if (!eligibleStatus || account.getEmailVerifiedAt() == null) {
			return Optional.empty();
		}
		return verificationTokenService.issueIfCooldownElapsed(
						account,
						VerificationTokenPurpose.PASSWORD_RESET,
						requiredIssuedAt,
						securityProperties.passwordResetRequestCooldown())
				.map(token -> {
					securityEventRecorder.record(
							SecurityEventType.PASSWORD_RESET_REQUESTED,
							SecurityEventResult.SUCCESS,
							null,
							SecurityEventContext.account(account.getId()),
							requiredIssuedAt);
					return delivery(account, token);
				});
	}

	private Optional<UserAccount> findByEmailForUpdate(String email) {
		try {
			return userRepository.findByNormalizedEmailForUpdate(
					emailNormalizer.normalize(email));
		}
		catch (NullPointerException | IllegalArgumentException invalidEmail) {
			return Optional.empty();
		}
	}

	private static AccountTokenDelivery delivery(
			UserAccount account,
			IssuedVerificationToken token) {
		return new AccountTokenDelivery(
				account.getEmail(),
				account.getFirstName(),
				token);
	}
}
