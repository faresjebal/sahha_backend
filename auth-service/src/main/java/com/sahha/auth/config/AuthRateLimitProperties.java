package com.sahha.auth.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sahha.auth.rate-limit")
public record AuthRateLimitProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("30") int loginMaximumAttempts,
		@DefaultValue("PT15M") Duration loginWindow,
		@DefaultValue("5") int registrationMaximumAttempts,
		@DefaultValue("PT1H") Duration registrationWindow,
		@DefaultValue("5") int emailRequestMaximumAttempts,
		@DefaultValue("PT1H") Duration emailRequestWindow,
		@DefaultValue("20") int tokenConfirmationMaximumAttempts,
		@DefaultValue("PT15M") Duration tokenConfirmationWindow,
		@DefaultValue("60") int refreshMaximumAttempts,
		@DefaultValue("PT5M") Duration refreshWindow) {

	public AuthRateLimitProperties {
		requirePolicy(loginMaximumAttempts, loginWindow, "login");
		requirePolicy(
				registrationMaximumAttempts,
				registrationWindow,
				"registration");
		requirePolicy(
				emailRequestMaximumAttempts,
				emailRequestWindow,
				"emailRequest");
		requirePolicy(
				tokenConfirmationMaximumAttempts,
				tokenConfirmationWindow,
				"tokenConfirmation");
		requirePolicy(refreshMaximumAttempts, refreshWindow, "refresh");
	}

	private static void requirePolicy(
			int maximumAttempts,
			Duration window,
			String policyName) {
		if (maximumAttempts < 1) {
			throw new IllegalArgumentException(
					policyName + "MaximumAttempts must be at least one");
		}
		if (window == null || window.isZero() || window.isNegative()) {
			throw new IllegalArgumentException(
					policyName + "Window must be positive");
		}
	}
}
