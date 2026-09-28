package com.sahha.auth.service.useraccountservice;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.entity.UserAccount;
import com.sahha.auth.dto.response.CurrentAccountResponse;
import com.sahha.auth.entity.SecurityEventResult;
import com.sahha.auth.entity.SecurityEventType;
import com.sahha.auth.exception.InvalidAuthenticationException;
import com.sahha.auth.repository.UserAccountRepository;
import com.sahha.auth.security.PasswordPolicy;
import com.sahha.auth.service.usersessionservice.UserSessionService;
import com.sahha.auth.service.securityeventservice.SecurityEventContext;
import com.sahha.auth.service.securityeventservice.SecurityEventRecorder;

@Service
public class AccountSecurityService {

	private final UserAccountRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final PasswordPolicy passwordPolicy;
	private final UserSessionService sessionService;
	private final SecurityEventRecorder securityEventRecorder;

	public AccountSecurityService(
			UserAccountRepository userRepository,
			PasswordEncoder passwordEncoder,
			PasswordPolicy passwordPolicy,
			UserSessionService sessionService,
			SecurityEventRecorder securityEventRecorder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.passwordPolicy = passwordPolicy;
		this.sessionService = sessionService;
		this.securityEventRecorder = securityEventRecorder;
	}

	@Transactional(readOnly = true)
	public CurrentAccountResponse currentAccount(UUID userId) {
		Objects.requireNonNull(userId, "userId must not be null");
		return userRepository.findById(userId)
				.map(CurrentAccountResponse::from)
				.orElseThrow(InvalidAuthenticationException::new);
	}

	@Transactional
	public CurrentAccountResponse updateProfile(UUID userId,
			com.sahha.auth.dto.request.UpdateAccountProfileRequest request, Instant now) {
		UserAccount account = userRepository.findByIdForUpdate(userId)
				.orElseThrow(InvalidAuthenticationException::new);
		if (account.getVersion() != request.version()) {
			throw new IllegalStateException("account profile version changed");
		}
		account.updateProfile(request.firstName(), request.lastName(), request.phoneNumber(), now);
		userRepository.saveAndFlush(account);
		securityEventRecorder.record(SecurityEventType.ACCOUNT_PROFILE_UPDATED,
				SecurityEventResult.SUCCESS, null, SecurityEventContext.account(userId), now);
		return CurrentAccountResponse.from(account);
	}

	@Transactional(noRollbackFor = InvalidAuthenticationException.class)
	public void changePassword(
			UUID userId,
			CharSequence currentRawPassword,
			CharSequence newRawPassword,
			Instant changedAt) {
		Objects.requireNonNull(userId, "userId must not be null");
		Objects.requireNonNull(
				currentRawPassword,
				"currentRawPassword must not be null");
		Instant requiredChangedAt = Objects.requireNonNull(
				changedAt,
				"changedAt must not be null");
		UserAccount account = userRepository.findByIdForUpdate(userId)
				.orElseThrow(InvalidAuthenticationException::new);
		if (!account.canAuthenticate()
				|| !passwordEncoder.matches(
						currentRawPassword,
						account.getPasswordHash())) {
			throw new InvalidAuthenticationException();
		}
		passwordPolicy.validate(newRawPassword);
		if (passwordEncoder.matches(
				newRawPassword,
				account.getPasswordHash())) {
			throw new IllegalArgumentException(
					"new password must differ from current password");
		}
		account.changePassword(
				passwordEncoder.encode(newRawPassword),
				requiredChangedAt);
		userRepository.saveAndFlush(account);
		sessionService.revokeAllForSecurityChange(
				userId,
				requiredChangedAt,
				"PASSWORD_CHANGED");
		securityEventRecorder.record(
				SecurityEventType.PASSWORD_CHANGED,
				SecurityEventResult.SUCCESS,
				null,
				SecurityEventContext.account(userId),
				requiredChangedAt);
	}
}
