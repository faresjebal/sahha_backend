package com.sahha.auth.service.useraccountservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import com.sahha.auth.entity.VerificationTokenPurpose;
import com.sahha.auth.service.emailservice.AuthEmailDispatchService;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;

class PublicAccountWorkflowServiceTests {

	private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

	private final AccountRegistrationService registrationService =
			mock(AccountRegistrationService.class);
	private final AccountVerificationService verificationService =
			mock(AccountVerificationService.class);
	private final AuthEmailDispatchService emailDispatchService =
			mock(AuthEmailDispatchService.class);
	private final PublicAccountWorkflowService workflowService =
			new PublicAccountWorkflowService(
					registrationService,
					verificationService,
					emailDispatchService);

	@Test
	void registrationQueuesTheRawTokenOnlyForMailDelivery() {
		IssuedVerificationToken token = token(
				VerificationTokenPurpose.EMAIL_VERIFICATION);
		when(registrationService.register(
				"synthetic.user@example.com",
				"synthetic passphrase",
				"Synthetic",
				"User",
				"+21600000000",
				NOW))
				.thenReturn(new PendingRegistrationResult(
						UUID.randomUUID(),
						token));

		workflowService.register(
				"synthetic.user@example.com",
				"synthetic passphrase",
				"Synthetic",
				"User",
				"+21600000000",
				NOW);

		ArgumentCaptor<AccountTokenDelivery> delivery =
				ArgumentCaptor.forClass(AccountTokenDelivery.class);
		verify(emailDispatchService).dispatch(delivery.capture());
		org.junit.jupiter.api.Assertions.assertEquals(
				"synthetic.user@example.com",
				delivery.getValue().getRecipientEmail());
		org.junit.jupiter.api.Assertions.assertEquals(
				token,
				delivery.getValue().getIssuedToken());
	}

	@Test
	void duplicateRegistrationKeepsThePublicWorkflowEnumerationSafe() {
		when(registrationService.register(
				any(),
				any(),
				any(),
				any(),
				any(),
				any()))
				.thenThrow(new DataIntegrityViolationException("duplicate"));

		assertDoesNotThrow(() -> workflowService.register(
				"existing@example.com",
				"synthetic passphrase",
				"Synthetic",
				"User",
				null,
				NOW));

		verify(emailDispatchService, never()).dispatch(any());
	}

	@Test
	void unknownRecoveryRequestDoesNotQueueAnEmail() {
		when(verificationService.requestPasswordReset(
				"unknown@example.com",
				NOW))
				.thenReturn(Optional.empty());

		workflowService.requestPasswordReset("unknown@example.com", NOW);

		verify(emailDispatchService, never()).dispatch(any());
	}

	private static IssuedVerificationToken token(
			VerificationTokenPurpose purpose) {
		return new IssuedVerificationToken(
				UUID.randomUUID(),
				"synthetic-raw-token",
				purpose,
				NOW.plusSeconds(3_600));
	}
}
