package com.sahha.file.service.medicalfilescanservice;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.sahha.file.dto.response.MedicalFileScanResponse;
import com.sahha.file.exception.FileStorageUnavailableException;
import com.sahha.file.storage.ObjectStorageException;
import com.sahha.file.storage.PrivateObjectStorage;

@Service
public class SyntheticMedicalFileScanService {

	private final MedicalFileScanPersistenceService persistenceService;
	private final PrivateObjectStorage objectStorage;

	public SyntheticMedicalFileScanService(
			MedicalFileScanPersistenceService persistenceService,
			PrivateObjectStorage objectStorage) {
		this.persistenceService = persistenceService;
		this.objectStorage = objectStorage;
	}

	public MedicalFileScanResponse apply(
			UUID fileId,
			UUID organisationId,
			UUID actorUserId,
			FileScanDecision decision,
			String requestId) {
		if (decision == FileScanDecision.CLEAN) {
			String storageKey = persistenceService.pendingStorageKey(
					fileId, organisationId, actorUserId);
			try {
				if (!objectStorage.exists(storageKey)) {
					throw new FileStorageUnavailableException(
							new IllegalStateException("stored-object-not-found"));
				}
			}
			catch (ObjectStorageException unavailable) {
				throw new FileStorageUnavailableException(unavailable);
			}
			return persistenceService.apply(
					fileId, organisationId, actorUserId, decision, requestId)
					.response();
		}

		AppliedFileScanDecision applied = persistenceService.apply(
				fileId, organisationId, actorUserId, decision, requestId);
		try {
			objectStorage.remove(applied.storageKey());
		}
		catch (ObjectStorageException unavailable) {
			throw new FileStorageUnavailableException(unavailable);
		}
		return applied.response();
	}
}
