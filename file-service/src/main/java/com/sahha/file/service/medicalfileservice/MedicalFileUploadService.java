package com.sahha.file.service.medicalfileservice;

import java.io.InputStream;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.file.client.clinical.ClinicalAttachmentAccessClient;
import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.config.FileStorageProperties;
import com.sahha.file.dto.request.NegotiateMedicalFileUploadRequest;
import com.sahha.file.dto.response.MedicalFileUploadResponse;
import com.sahha.file.dto.response.MedicalFileUploadTicketResponse;
import com.sahha.file.exception.FileStorageUnavailableException;
import com.sahha.file.exception.InvalidFileUploadException;
import com.sahha.file.storage.ObjectStorageException;
import com.sahha.file.storage.PrivateObjectStorage;

@Service
public class MedicalFileUploadService {

	private final ClinicalAttachmentAccessClient clinicalClient;
	private final MedicalFileUploadPersistenceService persistenceService;
	private final UploadTicketCodec ticketCodec;
	private final PrivateObjectStorage objectStorage;
	private final FileStorageProperties properties;

	public MedicalFileUploadService(
			ClinicalAttachmentAccessClient clinicalClient,
			MedicalFileUploadPersistenceService persistenceService,
			UploadTicketCodec ticketCodec,
			PrivateObjectStorage objectStorage,
			FileStorageProperties properties) {
		this.clinicalClient = clinicalClient;
		this.persistenceService = persistenceService;
		this.ticketCodec = ticketCodec;
		this.objectStorage = objectStorage;
		this.properties = properties;
	}

	public MedicalFileUploadTicketResponse negotiate(
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			NegotiateMedicalFileUploadRequest request,
			String requestId) {
		validateDeclaration(request);
		ClinicalAttachmentContextResource context = clinicalClient.resolve(
				request.consultationId(), organisationId, actorUserId, accessToken);
		return persistenceService.create(
				context, actorUserId, request, ticketCodec.issue(), requestId);
	}

	public MedicalFileUploadResponse upload(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String uploadToken,
			String contentType,
			long contentLength,
			InputStream content,
			String requestId) {
		if (contentLength <= 0
				|| contentLength > properties.maximumObjectSize().toBytes()) {
			throw new InvalidFileUploadException();
		}
		ClaimedMedicalFileUpload claimed = persistenceService.claim(
				fileId, organisationId, actorUserId, uploadToken,
				contentType, contentLength);
		UploadIntegrityInputStream verified = new UploadIntegrityInputStream(
				content, claimed.declaredSize());
		try {
			objectStorage.put(
					claimed.storageKey(), verified, claimed.declaredSize(),
					claimed.contentType());
			if (verified.count() != claimed.declaredSize()) {
				throw new InvalidFileUploadException();
			}
			String checksum = verified.checksumSha256();
			if (claimed.expectedChecksumSha256() != null
					&& !claimed.expectedChecksumSha256().equals(checksum)) {
				throw new InvalidFileUploadException();
			}
			return persistenceService.complete(
					claimed, verified.count(), checksum, requestId);
		}
		catch (InvalidFileUploadException invalid) {
			removeAndFail(claimed, "UPLOAD_INTEGRITY_MISMATCH", requestId);
			throw invalid;
		}
		catch (ObjectStorageException storageFailure) {
			String reason = verified.count() == claimed.declaredSize()
					? "OBJECT_STORAGE_FAILURE" : "UPLOAD_SIZE_MISMATCH";
			removeAndFail(claimed, reason, requestId);
			if ("UPLOAD_SIZE_MISMATCH".equals(reason)) {
				throw new InvalidFileUploadException();
			}
			throw new FileStorageUnavailableException(storageFailure);
		}
		catch (RuntimeException unexpectedFailure) {
			removeAndFail(claimed, "UPLOAD_PROCESSING_FAILURE", requestId);
			throw unexpectedFailure;
		}
	}

	private void validateDeclaration(NegotiateMedicalFileUploadRequest request) {
		if (request.declaredSize() > properties.maximumObjectSize().toBytes()
				|| !properties.allows(request.contentType())) {
			throw new InvalidFileUploadException();
		}
	}

	private void removeAndFail(
			ClaimedMedicalFileUpload claimed,
			String reasonCode,
			String requestId) {
		try {
			objectStorage.remove(claimed.storageKey());
		}
		catch (RuntimeException ignored) {
			// The failed upload remains unavailable in PostgreSQL. Orphan cleanup is
			// an infrastructure recovery concern and never makes the file readable.
		}
		persistenceService.fail(claimed, reasonCode, requestId);
	}
}
