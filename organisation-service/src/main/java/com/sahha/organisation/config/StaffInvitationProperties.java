package com.sahha.organisation.config;

import java.time.Duration;
import java.util.Objects;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sahha.organisation.staff-invitation")
public record StaffInvitationProperties(Duration validity) {

	public StaffInvitationProperties {
		Objects.requireNonNull(validity, "validity must not be null");
		if (validity.compareTo(Duration.ofHours(1)) < 0
				|| validity.compareTo(Duration.ofDays(30)) > 0) {
			throw new IllegalArgumentException(
					"staff invitation validity must be between one hour and 30 days");
		}
	}
}
