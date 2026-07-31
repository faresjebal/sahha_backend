package com.sahha.auth.service.emailservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sahha.auth.config.AuthMailProperties;

class AuthEmailTemplateRendererTests {

	private final AuthEmailTemplateRenderer renderer =
			new AuthEmailTemplateRenderer(new AuthMailProperties(
					true,
					"Sahha",
					"sender@example.com",
					URI.create("http://localhost:5173"),
					"/verify-email",
					"/reset-password"));

	@Test
	void verificationEmailContainsTheFrontendLinkAndEscapesHtmlNames() {
		String rawToken = "synthetic_token-value";
		RenderedAuthEmail email = renderer.emailVerification(
				"<Synthetic>",
				rawToken,
				Instant.parse("2026-07-27T12:00:00Z"));

		assertTrue(email.getSubject().contains("Verify"));
		assertTrue(email.getPlainText().contains(
				"http://localhost:5173/verify-email?token=" + rawToken));
		assertTrue(email.getHtml().contains("&lt;Synthetic&gt;"));
		assertFalse(email.getHtml().contains("Hello <Synthetic>"));
		assertFalse(email.toString().contains(rawToken));
	}

	@Test
	void resetEmailContainsExpiryAndDoesNotExposeBodyInToString() {
		String rawToken = "synthetic-reset-token";
		Instant expiresAt = Instant.parse("2026-07-26T12:30:00Z");
		RenderedAuthEmail email = renderer.passwordReset(
				"Synthetic",
				rawToken,
				expiresAt);

		assertTrue(email.getPlainText().contains(
				"http://localhost:5173/reset-password?token=" + rawToken));
		assertTrue(email.getHtml().contains("2026-07-26T12:30:00Z"));
		assertFalse(email.toString().contains(rawToken));
		assertFalse(email.toString().contains("Synthetic"));
	}
}
