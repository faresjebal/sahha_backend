package com.sahha.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class RefreshToken {

	private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "session_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserSession session;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount user;

	@Column(name = "token_hash", nullable = false, length = 64, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private String tokenHash;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_token_id", updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private RefreshToken parentToken;

	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "replaced_by_token_id")
	@Getter(onMethod_ = @JsonIgnore)
	private RefreshToken replacedByToken;

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

	@Column(name = "created_ip", length = 45, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private String createdIp;

	@Column(name = "created_user_agent", length = 512, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private String createdUserAgent;

	@Version
	@Column(nullable = false)
	private long version;

	public static RefreshToken issueInitial(
			UserSession session,
			String tokenHash,
			Instant createdAt,
			Instant expiresAt,
			String createdIp,
			String createdUserAgent) {
		return issue(
				session,
				null,
				tokenHash,
				createdAt,
				expiresAt,
				createdIp,
				createdUserAgent);
	}

	public static RefreshToken issueReplacement(
			UserSession session,
			RefreshToken parentToken,
			String tokenHash,
			Instant createdAt,
			Instant expiresAt,
			String createdIp,
			String createdUserAgent) {
		RefreshToken requiredParent = Objects.requireNonNull(
				parentToken,
				"parentToken must not be null");
		if (requiredParent.usedAt == null || requiredParent.revokedAt == null) {
			throw new IllegalStateException(
					"parentToken must be consumed before a replacement is issued");
		}
		return issue(
				session,
				requiredParent,
				tokenHash,
				createdAt,
				expiresAt,
				createdIp,
				createdUserAgent);
	}

	public void consumeForRotation(Instant consumedAt) {
		if (usedAt != null || revokedAt != null) {
			throw new IllegalStateException("refresh token is no longer active");
		}
		Instant requiredConsumedAt = requireNotBeforeCreation(
				consumedAt,
				"consumedAt");
		if (!requiredConsumedAt.isBefore(expiresAt)) {
			throw new IllegalArgumentException(
					"an expired refresh token cannot be rotated");
		}
		this.usedAt = requiredConsumedAt;
		this.revokedAt = requiredConsumedAt;
		this.revocationReason = "ROTATED";
	}

	public void linkReplacement(RefreshToken replacement) {
		RefreshToken requiredReplacement = Objects.requireNonNull(
				replacement,
				"replacement must not be null");
		if (replacedByToken != null) {
			throw new IllegalStateException("refresh token already has a replacement");
		}
		if (usedAt == null || revokedAt == null) {
			throw new IllegalStateException(
					"refresh token must be consumed before linking a replacement");
		}
		if (requiredReplacement.parentToken == null
				|| !id.equals(requiredReplacement.parentToken.getId())) {
			throw new IllegalArgumentException(
					"replacement does not reference this parent token");
		}
		if (!session.getId().equals(requiredReplacement.session.getId())
				|| !user.getId().equals(requiredReplacement.user.getId())) {
			throw new IllegalArgumentException(
					"replacement must belong to the same session family");
		}
		this.replacedByToken = requiredReplacement;
	}

	public void revoke(Instant revokedAt, String reason) {
		if (this.revokedAt != null) {
			return;
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

	private static RefreshToken issue(
			UserSession session,
			RefreshToken parentToken,
			String tokenHash,
			Instant createdAt,
			Instant expiresAt,
			String createdIp,
			String createdUserAgent) {
		UserSession requiredSession = Objects.requireNonNull(
				session,
				"session must not be null");
		if (requiredSession.getStatus() != SessionStatus.ACTIVE) {
			throw new IllegalStateException(
					"refresh tokens can only be issued for active sessions");
		}
		if (parentToken != null
				&& (!requiredSession.getId().equals(parentToken.session.getId())
				|| !requiredSession.getUser().getId().equals(parentToken.user.getId()))) {
			throw new IllegalArgumentException(
					"parentToken must belong to the same session family");
		}

		Instant requiredCreatedAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		Instant requiredExpiresAt = Objects.requireNonNull(
				expiresAt,
				"expiresAt must not be null");
		if (!requiredExpiresAt.isAfter(requiredCreatedAt)) {
			throw new IllegalArgumentException("expiresAt must follow createdAt");
		}
		if (requiredCreatedAt.isBefore(requiredSession.getCreatedAt())) {
			throw new IllegalArgumentException(
					"createdAt must not precede session creation");
		}
		if (requiredExpiresAt.isAfter(requiredSession.getAbsoluteExpiresAt())) {
			throw new IllegalArgumentException(
					"refresh token cannot outlive its session");
		}

		RefreshToken token = new RefreshToken();
		token.id = UUID.randomUUID();
		token.session = requiredSession;
		token.user = requiredSession.getUser();
		token.tokenHash = requireSha256Hex(tokenHash, "tokenHash");
		token.parentToken = parentToken;
		token.createdAt = requiredCreatedAt;
		token.expiresAt = requiredExpiresAt;
		token.createdIp = normalizeOptional(createdIp, 45, "createdIp");
		token.createdUserAgent = normalizeOptional(
				createdUserAgent,
				512,
				"createdUserAgent");
		return token;
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

	private static String requireSha256Hex(String value, String fieldName) {
		String required = requireText(value, 64, fieldName);
		if (!SHA_256_HEX.matcher(required).matches()) {
			throw new IllegalArgumentException(
					fieldName + " must be a lowercase SHA-256 hexadecimal value");
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

	private static String normalizeOptional(
			String value,
			int maximumLength,
			String fieldName) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			return null;
		}
		if (trimmed.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " exceeds " + maximumLength + " characters");
		}
		return trimmed;
	}
}
