package com.sahha.auth.service.emailservice;

import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
		prefix = "sahha.auth.mail",
		name = "enabled",
		havingValue = "false",
		matchIfMissing = true)
public class DisabledEmailDeliveryService implements EmailDeliveryService {

	@Override
	public void sendEmailVerification(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		// Intentional local/test no-op. Production delivery must enable SMTP.
	}

	@Override
	public void sendPasswordReset(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		// Intentional local/test no-op. Production delivery must enable SMTP.
	}
}
