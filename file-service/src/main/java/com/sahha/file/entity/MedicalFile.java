package com.sahha.file.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "medical_file")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class MedicalFile {

	private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "consultation_id", nullable = false, updatable = false)
	private UUID consultationId;

	@Column(name = "patient_registration_id", nullable = false, updatable = false)
	private UUID patientRegistrationId;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "uploader_user_id", nullable = false, updatable = false)
	private UUID uploaderUserId;

	@Column(name = "original_filename", nullable = false, length = 255)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 127)
	private String contentType;

	@Column(name = "declared_size", nullable = false)
	private long declaredSize;

	@Column(name = "actual_size")
	private Long actualSize;

	@Column(name = "expected_checksum_sha256", length = 64)
	private String expectedChecksumSha256;

	@Column(name = "checksum_sha256", length = 64)
	private String checksumSha256;

	@Column(name = "storage_key", nullable = false, length = 512, updatable = false)
	private String storageKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "access_category", nullable = false, length = 32)
	private FileAccessCategory accessCategory;

	@Enumerated(EnumType.STRING)
	@Column(name = "upload_status", nullable = false, length = 24)
	private FileUploadStatus uploadStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "scan_status", nullable = false, length = 24)
	private FileScanStatus scanStatus;

	@Column(name = "upload_ticket_digest", nullable = false, length = 64)
	private String uploadTicketDigest;

	@Column(name = "upload_ticket_expires_at", nullable = false)
	private Instant uploadTicketExpiresAt;

	@Column(name = "upload_ticket_used_at")
	private Instant uploadTicketUsedAt;

	@Column(name = "upload_failure_code", length = 64)
	private String uploadFailureCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "uploaded_at")
	private Instant uploadedAt;

	@Column(name = "available_at")
	private Instant availableAt;

	@Column(name = "rejected_at")
	private Instant rejectedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static MedicalFile negotiate(
			UUID id,
			UUID organisationId,
			UUID consultationId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID uploaderUserId,
			String originalFilename,
			String contentType,
			long declaredSize,
			String expectedChecksumSha256,
			String storageKey,
			String uploadTicketDigest,
			Instant createdAt,
			Instant uploadTicketExpiresAt) {
		if (declaredSize <= 0) {
			throw new IllegalArgumentException("declaredSize must be positive");
		}
		Instant requiredCreatedAt = Objects.requireNonNull(createdAt);
		Instant requiredExpiry = Objects.requireNonNull(uploadTicketExpiresAt);
		if (!requiredExpiry.isAfter(requiredCreatedAt)) {
			throw new IllegalArgumentException("upload ticket expiry must be in the future");
		}
		MedicalFile file = new MedicalFile();
		file.id = Objects.requireNonNull(id);
		file.organisationId = Objects.requireNonNull(organisationId);
		file.consultationId = Objects.requireNonNull(consultationId);
		file.patientRegistrationId = Objects.requireNonNull(patientRegistrationId);
		file.patientId = Objects.requireNonNull(patientId);
		file.uploaderUserId = Objects.requireNonNull(uploaderUserId);
		file.originalFilename = safeFilename(originalFilename);
		file.contentType = required(contentType, 127, "contentType").toLowerCase(Locale.ROOT);
		file.declaredSize = declaredSize;
		file.expectedChecksumSha256 = optionalChecksum(expectedChecksumSha256);
		file.storageKey = required(storageKey, 512, "storageKey");
		file.accessCategory = FileAccessCategory.CONSULTATION_DOCUMENT;
		file.uploadStatus = FileUploadStatus.NEGOTIATED;
		file.scanStatus = FileScanStatus.PENDING;
		file.uploadTicketDigest = checksum(uploadTicketDigest, "uploadTicketDigest");
		file.createdAt = requiredCreatedAt;
		file.uploadTicketExpiresAt = requiredExpiry;
		return file;
	}

	public void consumeUploadTicket(Instant usedAt) {
		Instant requiredUsedAt = Objects.requireNonNull(usedAt);
		if (uploadStatus != FileUploadStatus.NEGOTIATED || uploadTicketUsedAt != null) {
			throw new IllegalStateException("upload ticket is no longer available");
		}
		if (!requiredUsedAt.isBefore(uploadTicketExpiresAt)) {
			throw new IllegalStateException("upload ticket has expired");
		}
		uploadTicketUsedAt = requiredUsedAt;
	}

	public void markStored(long actualSize, String checksumSha256, Instant uploadedAt) {
		if (uploadStatus != FileUploadStatus.NEGOTIATED || uploadTicketUsedAt == null) {
			throw new IllegalStateException("file upload was not claimed");
		}
		if (actualSize != declaredSize) {
			throw new IllegalArgumentException("actual size does not match declaration");
		}
		String checksum = checksum(checksumSha256, "checksumSha256");
		if (expectedChecksumSha256 != null && !expectedChecksumSha256.equals(checksum)) {
			throw new IllegalArgumentException("checksum does not match declaration");
		}
		this.actualSize = actualSize;
		this.checksumSha256 = checksum;
		this.uploadedAt = Objects.requireNonNull(uploadedAt);
		this.uploadStatus = FileUploadStatus.STORED;
		this.uploadFailureCode = null;
	}

	public void markUploadFailed(String failureCode) {
		if (uploadStatus != FileUploadStatus.NEGOTIATED || uploadTicketUsedAt == null) {
			throw new IllegalStateException("file upload was not claimed");
		}
		uploadStatus = FileUploadStatus.FAILED;
		uploadFailureCode = required(failureCode, 64, "failureCode");
	}

	public void markClean(Instant scannedAt) {
		if (uploadStatus != FileUploadStatus.STORED || scanStatus != FileScanStatus.PENDING) {
			throw new IllegalStateException("file is not waiting for a scan result");
		}
		scanStatus = FileScanStatus.CLEAN;
		availableAt = Objects.requireNonNull(scannedAt);
	}

	public void markRejected(Instant scannedAt) {
		if (uploadStatus != FileUploadStatus.STORED || scanStatus != FileScanStatus.PENDING) {
			throw new IllegalStateException("file is not waiting for a scan result");
		}
		scanStatus = FileScanStatus.REJECTED;
		rejectedAt = Objects.requireNonNull(scannedAt);
	}

	public boolean isAvailable() {
		return uploadStatus == FileUploadStatus.STORED
				&& scanStatus == FileScanStatus.CLEAN;
	}

	private static String safeFilename(String value) {
		String filename = required(value, 255, "originalFilename");
		if (filename.indexOf('/') >= 0 || filename.indexOf('\\') >= 0
				|| filename.chars().anyMatch(Character::isISOControl)) {
			throw new IllegalArgumentException("originalFilename is unsafe");
		}
		return filename;
	}

	private static String optionalChecksum(String value) {
		return value == null || value.isBlank() ? null : checksum(value, "expectedChecksumSha256");
	}

	private static String checksum(String value, String fieldName) {
		String normalized = required(value, 64, fieldName).toLowerCase(Locale.ROOT);
		if (!SHA_256.matcher(normalized).matches()) {
			throw new IllegalArgumentException(fieldName + " must be lowercase SHA-256 hex");
		}
		return normalized;
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
