package com.sahha.auth.security;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.sahha.auth.config.AuthSecurityProperties;

@Component
public class PasswordPolicy {

	private final AuthSecurityProperties properties;

	public PasswordPolicy(AuthSecurityProperties properties) {
		this.properties = properties;
	}

	public void validate(CharSequence rawPassword) {
		Objects.requireNonNull(rawPassword, "rawPassword must not be null");
		int length = rawPassword.length();
		if (length < properties.minimumPasswordLength()
				|| length > properties.maximumPasswordLength()) {
			throw new IllegalArgumentException(
					"password length must be between %d and %d characters"
							.formatted(
									properties.minimumPasswordLength(),
									properties.maximumPasswordLength()));
		}

		boolean containsNonWhitespace = false;
		for (int index = 0; index < length; index++) {
			if (!Character.isWhitespace(rawPassword.charAt(index))) {
				containsNonWhitespace = true;
				break;
			}
		}
		if (!containsNonWhitespace) {
			throw new IllegalArgumentException(
					"password must contain a non-whitespace character");
		}
	}
}
