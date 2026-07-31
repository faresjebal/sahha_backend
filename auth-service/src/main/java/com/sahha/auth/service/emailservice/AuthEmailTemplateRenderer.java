package com.sahha.auth.service.emailservice;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import com.sahha.auth.config.AuthMailProperties;

@Component
public class AuthEmailTemplateRenderer {

	private final AuthMailProperties properties;

	public AuthEmailTemplateRenderer(AuthMailProperties properties) {
		this.properties = properties;
	}

	public RenderedAuthEmail emailVerification(
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		String link = link(properties.emailVerificationPath(), rawToken);
		return render(
				"Verify your Sahha email",
				recipientFirstName,
				"Verify your email",
				"Confirm this email address to activate your Sahha account.",
				"Verify email",
				link,
				expiresAt,
				"If you did not create a Sahha account, you can ignore this email.");
	}

	public RenderedAuthEmail passwordReset(
			String recipientFirstName,
			String rawToken,
			Instant expiresAt) {
		String link = link(properties.passwordResetPath(), rawToken);
		return render(
				"Reset your Sahha password",
				recipientFirstName,
				"Reset your password",
				"Use the secure link below to choose a new Sahha password.",
				"Reset password",
				link,
				expiresAt,
				"If you did not request a password reset, you can ignore this email.");
	}

	private String link(String path, String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			throw new IllegalArgumentException("rawToken must not be blank");
		}
		return UriComponentsBuilder
				.fromUri(properties.frontendBaseUrl())
				.path(path)
				.queryParam("token", rawToken)
				.build()
				.encode()
				.toUriString();
	}

	private RenderedAuthEmail render(
			String subject,
			String recipientFirstName,
			String heading,
			String message,
			String action,
			String link,
			Instant expiresAt,
			String ignoreMessage) {
		String firstName = requireText(recipientFirstName, "recipientFirstName");
		Instant requiredExpiry = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
		String expiry = DateTimeFormatter.ISO_INSTANT.format(requiredExpiry);
		String safeFirstName = HtmlUtils.htmlEscape(firstName);
		String safeLink = HtmlUtils.htmlEscape(link);

		String plainText = """
				Hello %s,

				%s

				%s

				This link expires at %s.

				%s

				Sahha
				""".formatted(
						firstName,
						message,
						link,
						expiry,
						ignoreMessage);

		String html = """
				<!doctype html>
				<html lang="en">
				<head>
				  <meta charset="utf-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1">
				  <title>%s</title>
				</head>
				<body style="margin:0;background:#f3f7f5;color:#173b33;font-family:Arial,sans-serif;">
				  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
				      style="background:#f3f7f5;padding:32px 16px;">
				    <tr>
				      <td align="center">
				        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0"
				            style="max-width:600px;background:#ffffff;border-radius:18px;overflow:hidden;
				            box-shadow:0 12px 30px rgba(23,59,51,.08);">
				          <tr>
				            <td style="padding:28px 36px;background:#173b33;color:#ffffff;">
				              <strong style="font-size:24px;letter-spacing:.3px;">Sahha</strong>
				              <div style="margin-top:6px;color:#cfe2db;font-size:14px;">
				                Secure healthcare access
				              </div>
				            </td>
				          </tr>
				          <tr>
				            <td style="padding:36px;">
				              <p style="margin:0 0 18px;">Hello %s,</p>
				              <h1 style="margin:0 0 16px;font-size:28px;line-height:1.2;">%s</h1>
				              <p style="margin:0 0 26px;color:#48665f;line-height:1.6;">%s</p>
				              <a href="%s"
				                  style="display:inline-block;padding:13px 22px;border-radius:10px;
				                  background:#2f806d;color:#ffffff;text-decoration:none;font-weight:700;">
				                %s
				              </a>
				              <p style="margin:26px 0 8px;color:#48665f;font-size:13px;">
				                This secure link expires at %s.
				              </p>
				              <p style="margin:0;color:#6c817c;font-size:13px;line-height:1.5;">%s</p>
				            </td>
				          </tr>
				        </table>
				      </td>
				    </tr>
				  </table>
				</body>
				</html>
				""".formatted(
						HtmlUtils.htmlEscape(subject),
						safeFirstName,
						HtmlUtils.htmlEscape(heading),
						HtmlUtils.htmlEscape(message),
						safeLink,
						HtmlUtils.htmlEscape(action),
						HtmlUtils.htmlEscape(expiry),
						HtmlUtils.htmlEscape(ignoreMessage));

		return new RenderedAuthEmail(subject, plainText, html);
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.strip();
	}
}
