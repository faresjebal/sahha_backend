package com.sahha.auth.service.useraccountservice;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSecurityProperties;
import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.repository.UserAccountRepository;

@Service
public class AccountAuthenticationService {

	private static final String DUMMY_PASSWORD =
			"constant-time-unknown-account-password";

	private final UserAccountRepository userRepository;
	private final EmailNormalizer emailNormalizer;
	private final PasswordEncoder passwordEncoder;
	private final AuthSecurityProperties securityProperties;
	private final String dummyPasswordHash;

	public AccountAuthenticationService(
			UserAccountRepository userRepository,
			EmailNormalizer emailNormalizer,
			PasswordEncoder passwordEncoder,
			AuthSecurityProperties securityProperties) {
		this.userRepository = userRepository;
		this.emailNormalizer = emailNormalizer;
		this.passwordEncoder = passwordEncoder;
		this.securityProperties = securityProperties;
		this.dummyPasswordHash = passwordEncoder.encode(DUMMY_PASSWORD);
	}

	@Transactional
	public AuthenticationResult authenticate(
			String email,
			CharSequence rawPassword,
			Instant attemptedAt) {
		Objects.requireNonNull(rawPassword, "rawPassword must not be null");
		Objects.requireNonNull(attemptedAt, "attemptedAt must not be null");

		String normalizedEmail;
		try {
			normalizedEmail = emailNormalizer.normalize(email);
		}
		catch (RuntimeException invalidEmail) {
			passwordEncoder.matches(rawPassword, dummyPasswordHash);
			return AuthenticationResult.denied();
		}

		Optional<UserAccount> accountResult =
				userRepository.findByNormalizedEmailForUpdate(normalizedEmail);
		if (accountResult.isEmpty()) {
			passwordEncoder.matches(rawPassword, dummyPasswordHash);
			return AuthenticationResult.denied();
		}

		UserAccount account = accountResult.orElseThrow();
		account.releaseExpiredLock(attemptedAt);
		boolean passwordMatches = passwordEncoder.matches(
				rawPassword,
				account.getPasswordHash());
		if (!account.canAuthenticate()) {
			return AuthenticationResult.denied();
		}
		if (!passwordMatches) {
			account.recordFailedLogin(
					attemptedAt,
					securityProperties.maximumFailedLoginAttempts(),
					securityProperties.loginLockDuration());
			return AuthenticationResult.denied();
		}

		account.recordSuccessfulLogin(attemptedAt);
		return AuthenticationResult.success(account.getId());
	}
}
