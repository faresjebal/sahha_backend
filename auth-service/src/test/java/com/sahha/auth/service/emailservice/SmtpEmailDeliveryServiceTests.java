package com.sahha.auth.service.emailservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import com.sahha.auth.config.AuthMailProperties;

class SmtpEmailDeliveryServiceTests {

	@Test
	void verificationEmailUsesTheConfiguredSenderAndRecipient() throws Exception {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		MimeMessage mimeMessage = new MimeMessage(
				Session.getInstance(new Properties()));
		when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
		AuthMailProperties properties = new AuthMailProperties(
				true,
				"Sahha",
				"sender@example.com",
				URI.create("http://localhost:5173"),
				"/verify-email",
				"/reset-password");
		SmtpEmailDeliveryService service = new SmtpEmailDeliveryService(
				mailSender,
				new AuthEmailTemplateRenderer(properties),
				properties);

		service.sendEmailVerification(
				"recipient@example.com",
				"Synthetic",
				"synthetic-token",
				Instant.parse("2026-07-27T12:00:00Z"));

		verify(mailSender).send(mimeMessage);
		assertEquals(
				"recipient@example.com",
				((InternetAddress) mimeMessage.getAllRecipients()[0]).getAddress());
		assertEquals(
				"sender@example.com",
				((InternetAddress) mimeMessage.getFrom()[0]).getAddress());
		assertEquals("Sahha", ((InternetAddress) mimeMessage.getFrom()[0])
				.getPersonal());
		assertEquals("Verify your Sahha email", mimeMessage.getSubject());

		ByteArrayOutputStream serialized = new ByteArrayOutputStream();
		mimeMessage.writeTo(serialized);
		String rawMessage = serialized.toString(StandardCharsets.UTF_8);
		assertTrue(rawMessage.contains("synthetic-token"));
		assertTrue(rawMessage.contains("Verify your email"));
	}
}
