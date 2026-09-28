package com.sahha.file.entity;

import java.time.Instant;
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

@Entity
@Table(name = "file_download_grant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class FileDownloadGrant {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "medical_file_id", nullable = false, updatable = false)
	private UUID medicalFileId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "access_scope", nullable = false, length = 24, updatable = false)
    private DownloadAccessScope accessScope;

	@Column(name = "token_digest", nullable = false, length = 64, updatable = false)
	private String tokenDigest;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@Column(name = "request_id", nullable = false, length = 128, updatable = false)
	private String requestId;

	@Version
	@Column(nullable = false)
	private long version;

	public static FileDownloadGrant issue(
			MedicalFile file,
			UUID actorUserId,
			String tokenDigest,
			String requestId,
			Instant issuedAt,
			Instant expiresAt) {
        return issue(file, actorUserId, tokenDigest, requestId, issuedAt, expiresAt, DownloadAccessScope.OWN);
    }

    public static FileDownloadGrant issue(MedicalFile file, UUID actorUserId, String tokenDigest,
            String requestId, Instant issuedAt, Instant expiresAt, DownloadAccessScope scope) {
        if (scope == null || scope == DownloadAccessScope.LEGACY) throw new IllegalArgumentException("invalid grant scope");
        MedicalFile requiredFile = Objects.requireNonNull(file);
		if (!requiredFile.isAvailable()) {
			throw new IllegalStateException("file is not available");
		}
		Instant requiredIssuedAt = Objects.requireNonNull(issuedAt);
		Instant requiredExpiresAt = Objects.requireNonNull(expiresAt);
		if (!requiredExpiresAt.isAfter(requiredIssuedAt)) {
			throw new IllegalArgumentException("download grant expiry must be in the future");
		}
		FileDownloadGrant grant = new FileDownloadGrant();
		grant.id = UUID.randomUUID();
		grant.medicalFileId = requiredFile.getId();
		grant.organisationId = requiredFile.getOrganisationId();
        grant.actorUserId = Objects.requireNonNull(actorUserId);
        grant.accessScope = scope;
		grant.tokenDigest = required(tokenDigest, 64, "tokenDigest");
		grant.requestId = required(requestId, 128, "requestId");
		grant.issuedAt = requiredIssuedAt;
		grant.expiresAt = requiredExpiresAt;
		return grant;
	}

	public void consume(Instant consumedAt) {
		Instant requiredConsumedAt = Objects.requireNonNull(consumedAt);
		if (usedAt != null) {
			throw new IllegalStateException("download grant was already used");
		}
		if (!requiredConsumedAt.isBefore(expiresAt)) {
			throw new IllegalStateException("download grant has expired");
		}
		usedAt = requiredConsumedAt;
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
