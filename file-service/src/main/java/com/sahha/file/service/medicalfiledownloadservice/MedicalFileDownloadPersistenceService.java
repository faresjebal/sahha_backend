package com.sahha.file.service.medicalfiledownloadservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.config.FileStorageProperties;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.dto.response.MedicalFileResource;
import com.sahha.file.entity.FileAuditEvent;
import com.sahha.file.entity.FileAuditEventType;
import com.sahha.file.entity.FileAuditResult;
import com.sahha.file.entity.FileDownloadGrant;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.exception.FileDownloadConflictException;
import com.sahha.file.exception.FileDownloadGrantExpiredException;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.repository.FileAuditEventRepository;
import com.sahha.file.repository.FileDownloadGrantRepository;
import com.sahha.file.repository.MedicalFileRepository;

@Service
public class MedicalFileDownloadPersistenceService {

	private final MedicalFileRepository fileRepository;
	private final FileDownloadGrantRepository grantRepository;
	private final FileAuditEventRepository auditRepository;
	private final DownloadGrantTokenCodec tokenCodec;
	private final FileStorageProperties properties;
	private final Clock clock;

	public MedicalFileDownloadPersistenceService(
			MedicalFileRepository fileRepository,
			FileDownloadGrantRepository grantRepository,
			FileAuditEventRepository auditRepository,
			DownloadGrantTokenCodec tokenCodec,
			FileStorageProperties properties,
			Clock clock) {
		this.fileRepository = fileRepository;
		this.grantRepository = grantRepository;
		this.auditRepository = auditRepository;
		this.tokenCodec = tokenCodec;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<MedicalFileResource> listOwned(
			ClinicalAttachmentContextResource context,
			UUID actorUserId) {
		return fileRepository
				.findByOrganisationIdAndConsultationIdAndPatientRegistrationIdAndPatientIdAndUploaderUserIdOrderByCreatedAtDesc(
						context.organisationId(), context.consultationId(),
						context.patientRegistrationId(), context.patientId(), actorUserId)
				.stream()
				.map(MedicalFileDownloadPersistenceService::resource)
				.toList();
	}

	@Transactional(readOnly = true)
	public MedicalFileAccessSnapshot findOwned(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		MedicalFile file = owned(fileId, organisationId, actorUserId);
		return new MedicalFileAccessSnapshot(
				file.getId(), file.getOrganisationId(), file.getConsultationId(),
				file.getPatientRegistrationId(), file.getPatientId(),
				file.getUploaderUserId());
	}

	@Transactional(noRollbackFor = FileDownloadConflictException.class)
	public MedicalFileDownloadGrantResponse issue(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String requestId) {
		MedicalFile file = lockOwned(fileId, organisationId, actorUserId);
		Instant now = Instant.now(clock);
		if (!file.isAvailable()) {
			audit(file, actorUserId, FileAuditEventType.ACCESS_DENIED,
					FileAuditResult.DENIED, "FILE_NOT_AVAILABLE", requestId, now);
			throw new FileDownloadConflictException();
		}
		IssuedDownloadGrant token = tokenCodec.issue();
		FileDownloadGrant grant = grantRepository.saveAndFlush(
				FileDownloadGrant.issue(
						file, actorUserId, token.digest(), requestId, now,
						now.plus(properties.downloadGrantTtl())));
		audit(file, actorUserId, FileAuditEventType.DOWNLOAD_GRANT_ISSUED,
				FileAuditResult.SUCCEEDED, null, requestId, now);
		return new MedicalFileDownloadGrantResponse(
				grant.getId(), file.getId(),
				"/api/v1/files/%s/content".formatted(file.getId()),
				token.value(), grant.getExpiresAt());
	}

	@Transactional(noRollbackFor = {
		FileResourceNotFoundException.class,
		FileDownloadConflictException.class,
		FileDownloadGrantExpiredException.class
	})
	public ClaimedMedicalFileDownload claim(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String presentedToken,
			String requestId) {
		MedicalFile file = lockOwned(fileId, organisationId, actorUserId);
		Instant now = Instant.now(clock);
		String tokenDigest = tokenCodec.digestPresented(presentedToken);
		if (tokenDigest == null) {
			deny(file, actorUserId, "INVALID_DOWNLOAD_GRANT", requestId, now);
			throw new FileResourceNotFoundException();
		}
		FileDownloadGrant grant = grantRepository
				.findForUpdateByMedicalFileIdAndOrganisationIdAndActorUserIdAndTokenDigest(
						fileId, organisationId, actorUserId, tokenDigest)
				.orElse(null);
		if (grant == null) {
			deny(file, actorUserId, "INVALID_DOWNLOAD_GRANT", requestId, now);
			throw new FileResourceNotFoundException();
		}
		if (grant.getUsedAt() != null) {
			deny(file, actorUserId, "DOWNLOAD_GRANT_REPLAY", requestId, now);
			throw new FileDownloadConflictException();
		}
		if (!now.isBefore(grant.getExpiresAt())) {
			audit(file, actorUserId, FileAuditEventType.GRANT_EXPIRED,
					FileAuditResult.DENIED, "DOWNLOAD_GRANT_EXPIRED", requestId, now);
			throw new FileDownloadGrantExpiredException();
		}
		if (!file.isAvailable()) {
			deny(file, actorUserId, "FILE_NOT_AVAILABLE", requestId, now);
			throw new FileDownloadConflictException();
		}
		grant.consume(now);
		grantRepository.saveAndFlush(grant);
		audit(file, actorUserId, FileAuditEventType.DOWNLOADED,
				FileAuditResult.SUCCEEDED, "DOWNLOAD_STREAM_AUTHORIZED", requestId, now);
		return new ClaimedMedicalFileDownload(
				file.getId(), file.getOrganisationId(), actorUserId,
				file.getStorageKey(), file.getOriginalFilename(), file.getContentType(),
				file.getActualSize());
	}

	@Transactional
	public void recordStorageFailure(
			ClaimedMedicalFileDownload claimed,
			String requestId) {
		MedicalFile file = lockOwned(
				claimed.fileId(), claimed.organisationId(), claimed.actorUserId());
		audit(file, claimed.actorUserId(), FileAuditEventType.DOWNLOADED,
				FileAuditResult.FAILED, "OBJECT_STORAGE_FAILURE", requestId,
				Instant.now(clock));
	}

	@Transactional
	public void recordDenied(
			MedicalFileAccessSnapshot snapshot,
			String reasonCode,
			String requestId) {
		MedicalFile file = lockOwned(
				snapshot.fileId(), snapshot.organisationId(), snapshot.uploaderUserId());
		deny(file, snapshot.uploaderUserId(), reasonCode, requestId,
				Instant.now(clock));
	}

	private MedicalFile owned(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		return fileRepository.findByIdAndOrganisationIdAndUploaderUserId(
				fileId, organisationId, actorUserId)
				.orElseThrow(FileResourceNotFoundException::new);
	}

	private MedicalFile lockOwned(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		return fileRepository.findForUpdateByIdAndOrganisationIdAndUploaderUserId(
				fileId, organisationId, actorUserId)
				.orElseThrow(FileResourceNotFoundException::new);
	}

	private void deny(
			MedicalFile file,
			UUID actorUserId,
			String reasonCode,
			String requestId,
			Instant now) {
		audit(file, actorUserId, FileAuditEventType.ACCESS_DENIED,
				FileAuditResult.DENIED, reasonCode, requestId, now);
	}

	private void audit(
			MedicalFile file,
			UUID actorUserId,
			FileAuditEventType eventType,
			FileAuditResult result,
			String reasonCode,
			String requestId,
			Instant now) {
		auditRepository.saveAndFlush(FileAuditEvent.record(
				file, actorUserId, eventType, result, reasonCode, requestId, now));
	}

	private static MedicalFileResource resource(MedicalFile file) {
		long size = file.getActualSize() == null
				? file.getDeclaredSize() : file.getActualSize();
		return new MedicalFileResource(
				file.getId(), file.getConsultationId(), file.getOriginalFilename(),
				file.getContentType(), size, file.getUploadStatus().name(),
				file.getScanStatus().name(), file.isAvailable(), file.getCreatedAt(),
				file.getUploadedAt(), file.getAvailableAt(), file.getRejectedAt());
	}
}
