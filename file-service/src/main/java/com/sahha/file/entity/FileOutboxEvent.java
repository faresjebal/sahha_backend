package com.sahha.file.entity;

import java.time.Instant;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "file_outbox_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class FileOutboxEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "file_audit_event_id", nullable = false, updatable = false)
	private UUID fileAuditEventId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "medical_file_id", nullable = false, updatable = false)
	private UUID medicalFileId;

	@Column(name = "event_type", nullable = false, length = 64, updatable = false)
	private String eventType;

	@Column(name = "aggregate_version", nullable = false, updatable = false)
	private long aggregateVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb", updatable = false)
	private Map<String, Object> payload;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(name = "publication_attempts", nullable = false)
	private int publicationAttempts;

	@Column(name = "last_attempt_at")
	private Instant lastAttemptAt;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "last_error_code", length = 128)
	private String lastErrorCode;

	@Column(name = "claim_token")
	private UUID claimToken;

	@Column(name = "claimed_at")
	private Instant claimedAt;

	@Column(name = "claim_until")
	private Instant claimUntil;

	@Version
	@Column(nullable = false)
	private long version;

	public static FileOutboxEvent pending(
			FileAuditEvent audit,
			MedicalFile file,
			String eventType,
			Map<String, Object> payload) {
		FileAuditEvent requiredAudit = Objects.requireNonNull(audit);
		MedicalFile requiredFile = Objects.requireNonNull(file);
		if (!requiredAudit.getMedicalFileId().equals(requiredFile.getId())) {
			throw new IllegalArgumentException("file audit and aggregate do not match");
		}
		FileOutboxEvent outbox = new FileOutboxEvent();
		outbox.id = UUID.randomUUID();
		outbox.fileAuditEventId = requiredAudit.getId();
		outbox.organisationId = requiredFile.getOrganisationId();
		outbox.medicalFileId = requiredFile.getId();
		outbox.eventType = required(eventType, 64, "eventType");
		outbox.aggregateVersion = requiredFile.getVersion();
		outbox.payload = Map.copyOf(new LinkedHashMap<>(Objects.requireNonNull(payload)));
		outbox.occurredAt = requiredAudit.getOccurredAt();
		outbox.nextAttemptAt = requiredAudit.getOccurredAt();
		return outbox;
	}

	public void claim(UUID claimToken, Instant claimedAt, Duration claimLease) {
		if (publishedAt != null) {
			throw new IllegalStateException("published event cannot be claimed");
		}
		Instant requiredClaimedAt = requireAttemptTime(claimedAt);
		if (claimUntil != null && claimUntil.isAfter(requiredClaimedAt)) {
			throw new IllegalStateException("outbox event has an active claim");
		}
		Duration requiredLease = positive(claimLease, "claimLease");
		this.claimToken = Objects.requireNonNull(claimToken);
		this.claimedAt = requiredClaimedAt;
		this.claimUntil = requiredClaimedAt.plus(requiredLease);
	}

	public void markPublished(UUID claimToken, Instant publishedAt) {
		requireClaim(claimToken);
		Instant attemptedAt = requireAttemptTime(publishedAt);
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = attemptedAt;
		this.publishedAt = attemptedAt;
		lastErrorCode = null;
		clearClaim();
	}

	public void markFailed(
			UUID claimToken,
			Instant attemptedAt,
			Duration retryDelay,
			String errorCode) {
		requireClaim(claimToken);
		Instant requiredAttempt = requireAttemptTime(attemptedAt);
		Duration requiredDelay = positive(retryDelay, "retryDelay");
		publicationAttempts = Math.addExact(publicationAttempts, 1);
		lastAttemptAt = requiredAttempt;
		nextAttemptAt = requiredAttempt.plus(requiredDelay);
		lastErrorCode = required(errorCode, 128, "errorCode");
		clearClaim();
	}

	private void requireClaim(UUID value) {
		if (claimToken == null || !claimToken.equals(value)) {
			throw new IllegalStateException("outbox claim is not owned");
		}
	}

	private void clearClaim() {
		claimToken = null;
		claimedAt = null;
		claimUntil = null;
	}

	private Instant requireAttemptTime(Instant value) {
		Instant required = Objects.requireNonNull(value);
		if (required.isBefore(occurredAt)) {
			throw new IllegalArgumentException("attempt precedes event");
		}
		return required;
	}

	private static Duration positive(Duration value, String fieldName) {
		Duration required = Objects.requireNonNull(value);
		if (required.isZero() || required.isNegative()) {
			throw new IllegalArgumentException(fieldName + " must be positive");
		}
		return required;
	}

	private static String required(String value, int maximumLength, String fieldName) {
		Objects.requireNonNull(value);
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
