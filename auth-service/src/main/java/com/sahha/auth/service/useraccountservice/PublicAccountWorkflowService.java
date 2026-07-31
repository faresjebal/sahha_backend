package com.sahha.auth.service.useraccountservice;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.sahha.auth.service.emailservice.AuthEmailDispatchService;

@Service
public class PublicAccountWorkflowService {

	private static final Logger LOGGER =
			LoggerFactory.getLogger(PublicAccountWorkflowService.class);

	private final AccountRegistrationService registrationService;
	private final AccountVerificationService verificationService;
	private final AuthEmailDispatchService emailDispatchService;

	public PublicAccountWorkflowService(
			AccountRegistrationService registrationService,
			AccountVerificationService verificationService,
			AuthEmailDispatchService emailDispatchService) {
		this.registrationService = registrationService;
		this.verificationService = verificationService;
		this.emailDispatchService = emailDispatchService;
	}

	public void register(
			String email,
			CharSequence rawPassword,
			String firstName,
			String lastName,
			String phoneNumber,
			Instant requestedAt) {
		try {
			PendingRegistrationResult registration =
					registrationService.register(
							email,
							rawPassword,
							firstName,
							lastName,
							phoneNumber,
							requestedAt);
			queue(new AccountTokenDelivery(
					email,
					firstName,
					registration.getVerificationToken()));
		}
		catch (DataIntegrityViolationException duplicateRegistration) {
			// The public response deliberately does not reveal account existence.
		}
	}

	public void confirmEmail(String rawToken, Instant confirmedAt) {
		verificationService.verifyEmail(rawToken, confirmedAt);
	}

	public void requestEmailVerification(String email, Instant requestedAt) {
		verificationService.requestEmailVerification(email, requestedAt)
				.ifPresent(this::queue);
	}

	public void requestPasswordReset(String email, Instant requestedAt) {
		verificationService.requestPasswordReset(email, requestedAt)
				.ifPresent(this::queue);
	}

	public void resetPassword(
			String rawToken,
			CharSequence newRawPassword,
			Instant changedAt) {
		verificationService.resetPassword(
				rawToken,
				newRawPassword,
				changedAt);
	}

	private void queue(AccountTokenDelivery delivery) {
		try {
			emailDispatchService.dispatch(delivery);
		}
		catch (RuntimeException exception) {
			LOGGER.warn(
					"Authentication email could not be queued because of {}",
					exception.getClass().getSimpleName());
		}
	}
}
