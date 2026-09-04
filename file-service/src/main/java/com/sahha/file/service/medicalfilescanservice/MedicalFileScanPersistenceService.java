package com.sahha.file.service.medicalfilescanservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.file.dto.response.MedicalFileScanResponse;
import com.sahha.file.entity.FileAuditEvent;
import com.sahha.file.entity.FileAuditEventType;
import com.sahha.file.entity.FileAuditResult;
import com.sahha.file.entity.FileOutboxEvent;
import com.sahha.file.entity.FileScanStatus;
import com.sahha.file.entity.FileUploadStatus;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.event.FileEventMapper;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.FileScanConflictException;
import com.sahha.file.repository.FileAuditEventRepository;
import com.sahha.file.repository.FileOutboxEventRepository;
import com.sahha.file.repository.MedicalFileRepository;

@Service
public class MedicalFileScanPersistenceService {

	private static final String AVAILABLE_EVENT = "medical-file.available.v1";
	private static final String REJECTED_EVENT = "medical-file.rejected.v1";
	private final MedicalFileRepository fileRepository;
	private final FileAuditEventRepository auditRepository;
	private final FileOutboxEventRepository outboxRepository;
	private final FileEventMapper eventMapper;
	private final Clock clock;

	public MedicalFileScanPersistenceService(
			MedicalFileRepository fileRepository,
			FileAuditEventRepository auditRepository,
			FileOutboxEventRepository outboxRepository,
			FileEventMapper eventMapper,
			Clock clock) {
		this.fileRepository = fileRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.eventMapper = eventMapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public String pendingStorageKey(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		MedicalFile file = owned(fileId, organisationId, actorUserId);
		if (file.getUploadStatus() == FileUploadStatus.STORED
				&& file.getScanStatus() == FileScanStatus.CLEAN) {
			return file.getStorageKey();
		}
		if (file.getUploadStatus() != FileUploadStatus.STORED
				|| file.getScanStatus() != FileScanStatus.PENDING) {
			throw new FileScanConflictException();
		}
		return file.getStorageKey();
	}

	@Transactional
	public AppliedFileScanDecision apply(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			FileScanDecision decision,
			String requestId) {
		MedicalFile file = fileRepository
				.findForUpdateByIdAndOrganisationIdAndUploaderUserId(
						fileId, organisationId, actorUserId)
				.orElseThrow(FileResourceNotFoundException::new);
		FileScanStatus target = decision == FileScanDecision.CLEAN
				? FileScanStatus.CLEAN : FileScanStatus.REJECTED;
		if (file.getUploadStatus() == FileUploadStatus.STORED
				&& file.getScanStatus() == target) {
			return result(file, true);
		}
		if (file.getUploadStatus() != FileUploadStatus.STORED
				|| file.getScanStatus() != FileScanStatus.PENDING) {
			throw new FileScanConflictException();
		}

		Instant now = Instant.now(clock);
		FileAuditEventType auditType;
		String reasonCode;
		String eventType;
		if (decision == FileScanDecision.CLEAN) {
			file.markClean(now);
			auditType = FileAuditEventType.SCAN_MARKED_CLEAN;
			reasonCode = "SYNTHETIC_LOCAL_CLEAN";
			eventType = AVAILABLE_EVENT;
		}
		else {
			file.markRejected(now);
			auditType = FileAuditEventType.SCAN_REJECTED;
			reasonCode = "SYNTHETIC_LOCAL_REJECTED";
			eventType = REJECTED_EVENT;
		}
		fileRepository.saveAndFlush(file);
		FileAuditEvent audit = auditRepository.saveAndFlush(FileAuditEvent.record(
				file, actorUserId, auditType, FileAuditResult.SUCCEEDED,
				reasonCode, requestId, now));
		outboxRepository.save(FileOutboxEvent.pending(
				audit, file, eventType,
				eventMapper.scanDecision(file, eventType, now)));
		return result(file, false);
	}

	private MedicalFile owned(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		return fileRepository
				.findByIdAndOrganisationIdAndUploaderUserId(
						fileId, organisationId, actorUserId)
				.orElseThrow(FileResourceNotFoundException::new);
	}

	private static AppliedFileScanDecision result(
			MedicalFile file,
			boolean alreadyApplied) {
		return new AppliedFileScanDecision(
				new MedicalFileScanResponse(
						file.getId(), file.getUploadStatus().name(),
						file.getScanStatus().name(), file.getAvailableAt(),
						file.getRejectedAt(), alreadyApplied),
				file.getStorageKey());
	}
}
