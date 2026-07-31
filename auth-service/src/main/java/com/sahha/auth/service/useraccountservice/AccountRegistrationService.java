package com.sahha.auth.service.useraccountservice;

import java.time.Instant;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.security.PasswordPolicy;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;
import com.sahha.auth.service.verificationtokenservice.VerificationTokenService;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class AccountRegistrationService {

	private final UserAccountRepository userRepository;
	private final EmailNormalizer emailNormalizer;
	private final PasswordPolicy passwordPolicy;
	private final PasswordEncoder passwordEncoder;
	private final VerificationTokenService verificationTokenService;
	private final SecurityEventRecorder securityEventRecorder;

	public AccountRegistrationService(
			UserAccountRepository userRepository,
			EmailNormalizer emailNormalizer,
			PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder,
			VerificationTokenService verificationTokenService,
			SecurityEventRecorder securityEventRecorder) {
		this.userRepository = userRepository;
		this.emailNormalizer = emailNormalizer;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
		this.verificationTokenService = verificationTokenService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional
	public PendingRegistrationResult register(
			String email,
			CharSequence rawPassword,
			String firstName,
			String lastName,
			String phoneNumber,
			Instant registeredAt) {
		Objects.requireNonNull(registeredAt, "registeredAt must not be null");
		passwordPolicy.validate(rawPassword);
		String normalizedEmail = emailNormalizer.normalize(email);
		String passwordHash = passwordEncoder.encode(rawPassword);

		UserAccount account = UserAccount.pendingRegistration(
				email,
				normalizedEmail,
				passwordHash,
				firstName,
				lastName,
				phoneNumber,
				registeredAt);
		userRepository.saveAndFlush(account);

		IssuedVerificationToken verificationToken =
				verificationTokenService.issue(
						account,
						VerificationTokenPurpose.EMAIL_VERIFICATION,
						registeredAt);
		securityEventRecorder.record(
				SecurityEventType.ACCOUNT_REGISTERED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.authentication(
						account.getId(),
						email,
						null,
						null,
						null),
				registeredAt);
		return new PendingRegistrationResult(
				account.getId(),
				verificationToken);
	}
}
