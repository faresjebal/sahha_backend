package com.sahha.file.service.medicalfileservice;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.config.FileStorageProperties;
import com.sahha.file.dto.request.NegotiateMedicalFileUploadRequest;
import com.sahha.file.dto.response.MedicalFileUploadResponse;
import com.sahha.file.dto.response.MedicalFileUploadTicketResponse;
import com.sahha.file.entity.FileAuditEvent;
import com.sahha.file.entity.FileAuditEventType;
import com.sahha.file.entity.FileAuditResult;
import com.sahha.file.entity.FileUploadStatus;
import com.sahha.file.entity.MedicalFile;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.FileUploadConflictException;
import com.sahha.file.exception.InvalidFileUploadException;
import com.sahha.file.repository.FileAuditEventRepository;
import com.sahha.file.repository.MedicalFileRepository;

@Service
public class MedicalFileUploadPersistenceService {

	private final MedicalFileRepository fileRepository;
	private final FileAuditEventRepository auditRepository;
	private final UploadTicketCodec ticketCodec;
	private final FileStorageProperties properties;
	private final Clock clock;

	public MedicalFileUploadPersistenceService(
			MedicalFileRepository fileRepository,
			FileAuditEventRepository auditRepository,
			UploadTicketCodec ticketCodec,
			FileStorageProperties properties,
			Clock clock) {
		this.fileRepository = fileRepository;
		this.auditRepository = auditRepository;
		this.ticketCodec = ticketCodec;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public MedicalFileUploadTicketResponse create(
			ClinicalAttachmentContextResource context,
			UUID actorUserId,
			NegotiateMedicalFileUploadRequest request,
			IssuedUploadTicket ticket,
			String requestId) {
		Instant now = Instant.now(clock);
		UUID fileId = UUID.randomUUID();
		String storageKey = "%s/%s/%s".formatted(
				context.organisationId(), context.consultationId(), fileId);
		MedicalFile file = MedicalFile.negotiate(
				fileId, context.organisationId(), context.consultationId(),
				context.patientRegistrationId(), context.patientId(), actorUserId,
				request.originalFilename(), normalize(request.contentType()),
				request.declaredSize(), normalizeChecksum(
						request.expectedChecksumSha256()), storageKey, ticket.digest(),
				now, now.plus(properties.uploadTicketTtl()));
		fileRepository.saveAndFlush(file);
		auditRepository.save(FileAuditEvent.record(
				file, actorUserId, FileAuditEventType.UPLOAD_NEGOTIATED,
				FileAuditResult.SUCCEEDED, null, requestId, now));
		return new MedicalFileUploadTicketResponse(
				file.getId(), "/api/v1/files/%s/content".formatted(file.getId()),
				ticket.value(), file.getUploadTicketExpiresAt(), file.getContentType(),
				file.getDeclaredSize(), file.getUploadStatus().name(),
				file.getScanStatus().name());
	}

	@Transactional
	public ClaimedMedicalFileUpload claim(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String uploadToken,
			String contentType,
			long contentLength) {
		MedicalFile file = lockOwned(fileId, organisationId, actorUserId);
		if (!ticketCodec.matches(file.getUploadTicketDigest(), uploadToken)) {
			throw new FileResourceNotFoundException();
		}
		if (file.getUploadStatus() != FileUploadStatus.NEGOTIATED
				|| file.getUploadTicketUsedAt() != null
				|| !Instant.now(clock).isBefore(file.getUploadTicketExpiresAt())) {
			throw new FileUploadConflictException();
		}
		if (contentLength != file.getDeclaredSize()
				|| !file.getContentType().equals(canonicalContentType(contentType))) {
			throw new InvalidFileUploadException();
		}
		file.consumeUploadTicket(Instant.now(clock));
		fileRepository.saveAndFlush(file);
		return new ClaimedMedicalFileUpload(
				file.getId(), file.getOrganisationId(), actorUserId,
				file.getStorageKey(), file.getContentType(), file.getDeclaredSize(),
				file.getExpectedChecksumSha256());
	}

	@Transactional
	public MedicalFileUploadResponse complete(
			ClaimedMedicalFileUpload claimed,
			long actualSize,
			String checksumSha256,
			String requestId) {
		MedicalFile file = lockOwned(
				claimed.fileId(), claimed.organisationId(), claimed.actorUserId());
		Instant now = Instant.now(clock);
		try {
			file.markStored(actualSize, checksumSha256, now);
		}
		catch (IllegalArgumentException | IllegalStateException invalidState) {
			throw new InvalidFileUploadException();
		}
		fileRepository.saveAndFlush(file);
		auditRepository.save(FileAuditEvent.record(
				file, claimed.actorUserId(), FileAuditEventType.UPLOAD_STORED,
				FileAuditResult.SUCCEEDED, null, requestId, now));
		return new MedicalFileUploadResponse(
				file.getId(), file.getUploadStatus().name(), file.getScanStatus().name(),
				file.getActualSize(), file.getChecksumSha256(), file.getUploadedAt());
	}

	@Transactional
	public void fail(
			ClaimedMedicalFileUpload claimed,
			String reasonCode,
			String requestId) {
		MedicalFile file = lockOwned(
				claimed.fileId(), claimed.organisationId(), claimed.actorUserId());
		if (file.getUploadStatus() != FileUploadStatus.NEGOTIATED
				|| file.getUploadTicketUsedAt() == null) {
			return;
		}
		Instant now = Instant.now(clock);
		file.markUploadFailed(reasonCode);
		fileRepository.saveAndFlush(file);
		auditRepository.save(FileAuditEvent.record(
				file, claimed.actorUserId(), FileAuditEventType.UPLOAD_FAILED,
				FileAuditResult.FAILED, reasonCode, requestId, now));
	}

	private MedicalFile lockOwned(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId) {
		return fileRepository
				.findForUpdateByIdAndOrganisationIdAndUploaderUserId(
						fileId, organisationId, actorUserId)
				.orElseThrow(FileResourceNotFoundException::new);
	}

	private static String normalize(String value) {
		if (value == null || value.isBlank()) {
			throw new InvalidFileUploadException();
		}
		return value.strip().toLowerCase(Locale.ROOT);
	}

	private static String canonicalContentType(String value) {
		try {
			MediaType mediaType = MediaType.parseMediaType(normalize(value));
			return mediaType.getType() + "/" + mediaType.getSubtype();
		}
		catch (RuntimeException invalidContentType) {
			throw new InvalidFileUploadException();
		}
	}

	private static String normalizeChecksum(String value) {
		return value == null || value.isBlank()
				? null : value.strip().toLowerCase(Locale.ROOT);
	}
}
