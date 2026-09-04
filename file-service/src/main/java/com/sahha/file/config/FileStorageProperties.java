package com.sahha.file.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("sahha.file.storage")
public record FileStorageProperties(
		@NotBlank String provider,
		@NotNull URI endpoint,
		@NotBlank String accessKey,
		@NotBlank String secretKey,
		@NotBlank String region,
		@NotBlank String bucket,
		@NotNull Set<@NotBlank String> allowedContentTypes,
		@NotNull DataSize maximumObjectSize,
		@NotNull Duration uploadTicketTtl,
		@NotNull Duration downloadGrantTtl,
		boolean syntheticCleanEnabled) {

	@AssertTrue(message = "file storage limits and lifetimes must be positive")
	public boolean hasPositiveLimits() {
		return maximumObjectSize != null && maximumObjectSize.toBytes() > 0
				&& allowedContentTypes != null && !allowedContentTypes.isEmpty()
				&& uploadTicketTtl != null && uploadTicketTtl.isPositive()
				&& downloadGrantTtl != null && downloadGrantTtl.isPositive();
	}

	public boolean allows(String contentType) {
		return contentType != null && allowedContentTypes.stream()
				.map(value -> value.toLowerCase(Locale.ROOT))
				.anyMatch(value -> value.equals(contentType.toLowerCase(Locale.ROOT)));
	}

	@Override
	public String toString() {
		return "FileStorageProperties[provider=" + provider
				+ ", endpoint=" + endpoint
				+ ", accessKey=<redacted>, secretKey=<redacted>, region=" + region
				+ ", bucket=" + bucket
				+ ", allowedContentTypes=" + allowedContentTypes
				+ ", maximumObjectSize=" + maximumObjectSize
				+ ", uploadTicketTtl=" + uploadTicketTtl
				+ ", downloadGrantTtl=" + downloadGrantTtl
				+ ", syntheticCleanEnabled=" + syntheticCleanEnabled + "]";
	}
}
