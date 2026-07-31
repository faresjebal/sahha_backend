package com.sahha.auth.service.emailservice;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.sahha.auth.config.AuthMailProperties;

@Service
@ConditionalOnProperty(
		prefix = "sahha.auth.mail",
		name = "enabled",
		havingValue = "true")
public class SmtpEmailDeliveryService implements EmailDeliveryService {

	private final JavaMailSender mailSender;
	private final AuthEmailTemplateRenderer templateRenderer;
	private final AuthMailProperties properties;

	public SmtpEmailDeliveryService(
			JavaMailSender mailSender,
			AuthEmailTemplateRenderer templateRenderer,
			AuthMailProperties properties) {
		this.mailSender = mailSender;
		this.templateRenderer = templateRenderer;
		this.properties = properties;
	}

	@Override
	public void sendEmailVerification(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		send(
				recipientEmail,
				templateRenderer.emailVerification(
						recipientFirstName,
						rawToken,
						expiresAt));
	}

	@Override
	public void sendPasswordReset(
			String recipientEmail,
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		send(
				recipientEmail,
				templateRenderer.passwordReset(
						recipientFirstName,
						rawToken,
						expiresAt));
	}

	private void send(String recipientEmail, RenderedAuthEmail email) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(
					message,
					MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
					StandardCharsets.UTF_8.name());
			helper.setFrom(properties.fromEmail(), properties.fromName());
			helper.setTo(recipientEmail);
			helper.setSubject(email.getSubject());
			helper.setText(email.getPlainText(), email.getHtml());
			mailSender.send(message);
		}
		catch (MessagingException | UnsupportedEncodingException exception) {
			throw new IllegalStateException(
					"authentication email could not be prepared",
					exception);
		}
	}
}
