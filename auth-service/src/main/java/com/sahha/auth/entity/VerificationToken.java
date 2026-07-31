package com.sahha.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "verification_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class VerificationToken {

	private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount user;

	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private String tokenHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32, updatable = false)
	@ToString.Include
	private VerificationTokenPurpose purpose;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revocation_reason", length = 255)
	private String revocationReason;

	@Version
	@Column(nullable = false)
	private long version;

	public static VerificationToken issue(
			UserAccount user,
			VerificationTokenPurpose purpose,
			String tokenHash,
			Instant createdAt,
			Instant expiresAt) {
		Instant requiredCreatedAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		Instant requiredExpiresAt = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
		if (!requiredExpiresAt.isAfter(requiredCreatedAt)) {
			throw new IllegalArgumentException("expiresAt must follow createdAt");
		}

		VerificationToken token = new VerificationToken();
		token.id = UUID.randomUUID();
		token.user = Objects.requireNonNull(user, "user must not be null");
		token.purpose = Objects.requireNonNull(purpose, "purpose must not be null");
		token.tokenHash = requireSha256Hex(tokenHash);
		token.createdAt = requiredCreatedAt;
		token.expiresAt = requiredExpiresAt;
		return token;
	}

	public void consume(Instant consumedAt) {
		if (usedAt != null || revokedAt != null) {
			throw new IllegalStateException("verification token is no longer active");
		}
		Instant requiredConsumedAt = requireNotBeforeCreation(
				consumedAt,
				"consumedAt");
		if (!requiredConsumedAt.isBefore(expiresAt)) {
			throw new IllegalStateException("verification token is invalid or expired");
		}
		this.usedAt = requiredConsumedAt;
	}

	public void revoke(Instant revokedAt, String reason) {
		if (this.revokedAt != null) {
			return;
		}
		if (usedAt != null) {
			throw new IllegalStateException("a consumed token cannot be revoked");
		}
		this.revokedAt = requireNotBeforeCreation(revokedAt, "revokedAt");
		this.revocationReason = requireText(reason, 255, "reason");
	}

	public boolean isActiveAt(Instant observedAt) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		return usedAt == null
				&& revokedAt == null
				&& requiredObservedAt.isBefore(expiresAt);
	}

	private Instant requireNotBeforeCreation(Instant value, String fieldName) {
		Instant required = Objects.requireNonNull(
				value,
				fieldName + " must not be null");
		if (required.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					fieldName + " must not precede token creation");
		}
		return required;
	}

	private static String requireSha256Hex(String value) {
		String required = requireText(value, 64, "tokenHash");
		if (!SHA_256_HEX.matcher(required).matches()) {
			throw new IllegalArgumentException(
					"tokenHash must be a lowercase SHA-256 hexadecimal value");
		}
		return required;
	}

	private static String requireText(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		if (trimmed.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " exceeds " + maximumLength + " characters");
		}
		return trimmed;
	}
}
