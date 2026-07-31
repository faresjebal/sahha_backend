package com.sahha.auth.service.emailservice;

import java.time.Instant;

public interface EmailDeliveryService {

	void sendEmailVerification(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt);

	void sendPasswordReset(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt);
}
