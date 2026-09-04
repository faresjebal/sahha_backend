package com.sahha.file.service.medicalfiledownloadservice;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.file.client.clinical.ClinicalAttachmentAccessClient;
import com.sahha.file.client.clinical.ClinicalAttachmentContextResource;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.dto.response.MedicalFileResource;
import com.sahha.file.exception.ClinicalContextUnavailableException;
import com.sahha.file.exception.FileAccessDeniedException;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.FileStorageUnavailableException;
import com.sahha.file.storage.ObjectStorageException;
import com.sahha.file.storage.PrivateObjectStorage;

@Service
public class MedicalFileDownloadService {

	private final ClinicalAttachmentAccessClient clinicalClient;
	private final MedicalFileDownloadPersistenceService persistenceService;
	private final PrivateObjectStorage objectStorage;

	public MedicalFileDownloadService(
			ClinicalAttachmentAccessClient clinicalClient,
			MedicalFileDownloadPersistenceService persistenceService,
			PrivateObjectStorage objectStorage) {
		this.clinicalClient = clinicalClient;
		this.persistenceService = persistenceService;
		this.objectStorage = objectStorage;
	}

	public List<MedicalFileResource> list(
			UUID consultationId,
			UUID organisationId,
			UUID actorUserId,
			String accessToken) {
		ClinicalAttachmentContextResource context = clinicalClient.resolve(
				consultationId, organisationId, actorUserId, accessToken);
		return persistenceService.listOwned(context, actorUserId);
	}

	public MedicalFileDownloadGrantResponse issueGrant(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String accessToken,
			String requestId) {
		MedicalFileAccessSnapshot snapshot = persistenceService.findOwned(
				fileId, organisationId, actorUserId);
		ClinicalAttachmentContextResource context;
		try {
			context = clinicalClient.resolve(
					snapshot.consultationId(), organisationId, actorUserId,
					accessToken);
		}
		catch (FileAccessDeniedException | FileResourceNotFoundException denied) {
			persistenceService.recordDenied(
					snapshot, "CLINICAL_RELATIONSHIP_DENIED", requestId);
			throw denied;
		}
		if (!snapshot.matches(context)) {
			persistenceService.recordDenied(
					snapshot, "CLINICAL_CONTEXT_MISMATCH", requestId);
			throw new ClinicalContextUnavailableException();
		}
		return persistenceService.issue(
				fileId, organisationId, actorUserId, requestId);
	}

	public AuthorizedMedicalFileDownload download(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			String downloadToken,
			String requestId) {
		ClaimedMedicalFileDownload claimed = persistenceService.claim(
				fileId, organisationId, actorUserId, downloadToken, requestId);
		try {
			return new AuthorizedMedicalFileDownload(
					claimed.fileId(), claimed.originalFilename(), claimed.contentType(),
					claimed.size(), objectStorage.get(claimed.storageKey()));
		}
		catch (ObjectStorageException storageFailure) {
			persistenceService.recordStorageFailure(claimed, requestId);
			throw new FileStorageUnavailableException(storageFailure);
		}
	}
}
