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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "user_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class UserSession {

	private static final Pattern SHA_256_HEX = Pattern.compile("^[0-9a-f]{64}$");

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@ToString.Include
	private SessionStatus status;

	@Column(name = "active_organisation_id")
	private UUID activeOrganisationId;

	@Column(name = "device_id_hash", nullable = false, length = 64)
	@Getter(onMethod_ = @JsonIgnore)
	private String deviceIdHash;

	@Column(name = "device_name", length = 120)
	private String deviceName;

	@Column(name = "user_agent", length = 512)
	@Getter(onMethod_ = @JsonIgnore)
	private String userAgent;

	@Column(name = "initial_ip", length = 45)
	@Getter(onMethod_ = @JsonIgnore)
	private String initialIp;

	@Column(name = "last_ip", length = 45)
	@Getter(onMethod_ = @JsonIgnore)
	private String lastIp;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "last_activity_at", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "idle_expires_at", nullable = false)
	private Instant idleExpiresAt;

	@Column(name = "absolute_expires_at", nullable = false, updatable = false)
	private Instant absoluteExpiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "revoked_by_user_id")
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount revokedBy;

	@Column(name = "revocation_reason", length = 255)
	private String revocationReason;

	@Column(name = "credential_version_at_creation", nullable = false, updatable = false)
	private int credentialVersionAtCreation;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static UserSession open(
			UserAccount user,
			UUID activeOrganisationId,
			String deviceIdHash,
			String deviceName,
			String userAgent,
			String initialIp,
			Instant createdAt,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		UserAccount requiredUser = Objects.requireNonNull(user, "user must not be null");
		Instant requiredCreatedAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		validateExpirationWindow(requiredCreatedAt, idleExpiresAt, absoluteExpiresAt);

		UserSession session = new UserSession();
		session.id = UUID.randomUUID();
		session.user = requiredUser;
		session.status = SessionStatus.ACTIVE;
		session.activeOrganisationId = activeOrganisationId;
		session.deviceIdHash = requireSha256Hex(deviceIdHash, "deviceIdHash");
		session.deviceName = normalizeOptional(deviceName, 120, "deviceName");
		session.userAgent = normalizeOptional(userAgent, 512, "userAgent");
		session.initialIp = normalizeOptional(initialIp, 45, "initialIp");
		session.lastIp = session.initialIp;
		session.createdAt = requiredCreatedAt;
		session.lastActivityAt = requiredCreatedAt;
		session.idleExpiresAt = idleExpiresAt;
		session.absoluteExpiresAt = absoluteExpiresAt;
		session.credentialVersionAtCreation = requiredUser.getCredentialVersion();
		session.updatedAt = requiredCreatedAt;
		return session;
	}

	public void recordActivity(
			Instant activityAt,
			Instant nextIdleExpiresAt,
			String lastIp) {
		requireActive();
		Instant requiredActivityAt = Objects.requireNonNull(
				activityAt,
				"activityAt must not be null");
		Instant requiredIdleExpiration = Objects.requireNonNull(
				nextIdleExpiresAt,
				"nextIdleExpiresAt must not be null");

		if (requiredActivityAt.isBefore(lastActivityAt)) {
			throw new IllegalArgumentException(
					"activityAt must not precede the last recorded activity");
		}
		if (!requiredActivityAt.isBefore(absoluteExpiresAt)) {
			throw new IllegalArgumentException(
					"activityAt must precede the absolute expiration");
		}
		if (!requiredActivityAt.isBefore(idleExpiresAt)) {
			throw new IllegalArgumentException(
					"an idle-expired session cannot record activity");
		}
		if (!requiredIdleExpiration.isAfter(requiredActivityAt)) {
			throw new IllegalArgumentException(
					"nextIdleExpiresAt must follow activityAt");
		}
		if (requiredIdleExpiration.isBefore(idleExpiresAt)) {
			throw new IllegalArgumentException(
					"idle expiration cannot move backwards");
		}
		if (requiredIdleExpiration.isAfter(absoluteExpiresAt)) {
			throw new IllegalArgumentException(
					"idle expiration cannot exceed absolute expiration");
		}

		this.lastActivityAt = requiredActivityAt;
		this.idleExpiresAt = requiredIdleExpiration;
		this.lastIp = normalizeOptional(lastIp, 45, "lastIp");
	}

	public void revoke(
			Instant revokedAt,
			UserAccount revokedBy,
			String revocationReason) {
		if (status == SessionStatus.REVOKED) {
			return;
		}
		requireActive();
		this.status = SessionStatus.REVOKED;
		this.revokedAt = requireNotBeforeCreation(revokedAt, "revokedAt");
		this.revokedBy = revokedBy;
		this.revocationReason = requireText(
				revocationReason,
				255,
				"revocationReason");
	}

	public void markCompromised(Instant compromisedAt, String reason) {
		if (status == SessionStatus.COMPROMISED) {
			return;
		}
		requireActive();
		this.status = SessionStatus.COMPROMISED;
		this.revokedAt = requireNotBeforeCreation(compromisedAt, "compromisedAt");
		this.revokedBy = null;
		this.revocationReason = requireText(reason, 255, "reason");
	}

	public void expire(Instant expirationObservedAt) {
		if (status == SessionStatus.EXPIRED) {
			return;
		}
		requireActive();
		Instant observedAt = Objects.requireNonNull(
				expirationObservedAt,
				"expirationObservedAt must not be null");
		Instant effectiveExpiration = idleExpiresAt.isBefore(absoluteExpiresAt)
				? idleExpiresAt
				: absoluteExpiresAt;
		if (observedAt.isBefore(effectiveExpiration)) {
			throw new IllegalArgumentException(
					"the session cannot expire before its effective expiration");
		}
		this.status = SessionStatus.EXPIRED;
	}

	public boolean isActiveAt(
			Instant observedAt,
			int currentCredentialVersion) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		return status == SessionStatus.ACTIVE
				&& credentialVersionAtCreation == currentCredentialVersion
				&& requiredObservedAt.isBefore(idleExpiresAt)
				&& requiredObservedAt.isBefore(absoluteExpiresAt);
	}

	@PrePersist
	void beforeInsert() {
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}

	@PreUpdate
	void beforeUpdate() {
		updatedAt = Instant.now();
	}

	private void requireActive() {
		if (status != SessionStatus.ACTIVE) {
			throw new IllegalStateException("session is not active");
		}
	}

	private Instant requireNotBeforeCreation(Instant value, String fieldName) {
		Instant required = Objects.requireNonNull(
				value,
				fieldName + " must not be null");
		if (required.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					fieldName + " must not precede session creation");
		}
		return required;
	}

	private static void validateExpirationWindow(
			Instant createdAt,
			Instant idleExpiresAt,
			Instant absoluteExpiresAt) {
		Instant requiredIdleExpiration = Objects.requireNonNull(
				idleExpiresAt,
				"idleExpiresAt must not be null");
		Instant requiredAbsoluteExpiration = Objects.requireNonNull(
				absoluteExpiresAt,
				"absoluteExpiresAt must not be null");
		if (!requiredIdleExpiration.isAfter(createdAt)) {
			throw new IllegalArgumentException(
					"idleExpiresAt must follow createdAt");
		}
		if (!requiredAbsoluteExpiration.isAfter(createdAt)) {
			throw new IllegalArgumentException(
					"absoluteExpiresAt must follow createdAt");
		}
		if (requiredIdleExpiration.isAfter(requiredAbsoluteExpiration)) {
			throw new IllegalArgumentException(
					"idleExpiresAt cannot exceed absoluteExpiresAt");
		}
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
