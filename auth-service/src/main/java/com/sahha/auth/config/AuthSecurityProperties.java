package com.sahha.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.security")
public record AuthSecurityProperties(
		@DefaultValue("12") int bcryptStrength,
		@DefaultValue("12") int minimumPasswordLength,
		@DefaultValue("128") int maximumPasswordLength,
		@DefaultValue("5") int maximumFailedLoginAttempts,
		@DefaultValue("PT15M") Duration loginLockDuration,
		@DefaultValue("PT168H") Duration sessionIdleLifetime,
		@DefaultValue("PT720H") Duration sessionAbsoluteLifetime,
		@DefaultValue("PT24H") Duration emailVerificationLifetime,
		@DefaultValue("PT30M") Duration passwordResetLifetime,
		@DefaultValue("PT30M") Duration emailChangeLifetime,
		@DefaultValue("PT2M") Duration emailVerificationRequestCooldown,
		@DefaultValue("PT2M") Duration passwordResetRequestCooldown) {

	public AuthSecurityProperties {
		if (bcryptStrength < 4 || bcryptStrength > 31) {
			throw new IllegalArgumentException(
					"bcryptStrength must be between 4 and 31");
		}
		if (minimumPasswordLength < 12) {
			throw new IllegalArgumentException(
					"minimumPasswordLength must be at least 12");
		}
		if (maximumPasswordLength < minimumPasswordLength) {
			throw new IllegalArgumentException(
					"maximumPasswordLength must not be less than minimumPasswordLength");
		}
		if (maximumFailedLoginAttempts < 1) {
			throw new IllegalArgumentException(
					"maximumFailedLoginAttempts must be at least one");
		}
		requirePositive(loginLockDuration, "loginLockDuration");
		requirePositive(sessionIdleLifetime, "sessionIdleLifetime");
		requirePositive(sessionAbsoluteLifetime, "sessionAbsoluteLifetime");
		if (sessionIdleLifetime.compareTo(sessionAbsoluteLifetime) > 0) {
			throw new IllegalArgumentException(
					"sessionIdleLifetime must not exceed sessionAbsoluteLifetime");
		}
		requirePositive(emailVerificationLifetime, "emailVerificationLifetime");
		requirePositive(passwordResetLifetime, "passwordResetLifetime");
		requirePositive(emailChangeLifetime, "emailChangeLifetime");
		requirePositive(
				emailVerificationRequestCooldown,
				"emailVerificationRequestCooldown");
		requirePositive(
				passwordResetRequestCooldown,
				"passwordResetRequestCooldown");
	}

	private static void requirePositive(Duration value, String fieldName) {
		if (value == null || value.isZero() || value.isNegative()) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
	}
}
