package com.sahha.file.service.medicalfiledownloadservice;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.sahha.file.client.communication.CommunicationShareAccessClient;
import com.sahha.file.dto.response.MedicalFileResource;
import com.sahha.file.dto.response.MedicalFileDownloadGrantResponse;
import com.sahha.file.exception.FileResourceNotFoundException;
import com.sahha.file.exception.FileStorageUnavailableException;
import com.sahha.file.exception.SharingContextUnavailableException;
import com.sahha.file.storage.ObjectStorageException;
import com.sahha.file.storage.PrivateObjectStorage;

@Service
public class SharedMedicalFileService {
    private final MedicalFileDownloadPersistenceService persistence;
    private final CommunicationShareAccessClient sharing;
    private final PrivateObjectStorage storage;

    public SharedMedicalFileService(MedicalFileDownloadPersistenceService persistence,
            CommunicationShareAccessClient sharing, PrivateObjectStorage storage) {
        this.persistence = persistence;
        this.sharing = sharing;
        this.storage = storage;
    }

    public MedicalFileResource metadata(UUID organisationId, UUID actorId, UUID patientId,
            UUID fileId, String token, String requestId) {
        var access = authorize(organisationId, actorId, patientId, fileId, token, requestId);
        return persistence.sharedMetadata(access.snapshot(), actorId, requestId);
    }

    public MedicalFileDownloadGrantResponse issue(UUID organisationId, UUID actorId,
            UUID patientId, UUID fileId, String token, String requestId) {
        var access = authorize(organisationId, actorId, patientId, fileId, token, requestId);
        return persistence.issueShared(access.snapshot(), actorId, requestId, access.validUntil());
    }

    public AuthorizedMedicalFileDownload download(UUID organisationId, UUID actorId,
            UUID patientId, UUID fileId, String token, String downloadToken, String requestId) {
        // A one-time download token is not a replacement for the live sharing decision.
        var access = authorize(organisationId, actorId, patientId, fileId, token, requestId);
        var claimed = persistence.claimShared(access.snapshot(), actorId, downloadToken, requestId);
        try {
            return new AuthorizedMedicalFileDownload(claimed.fileId(), claimed.originalFilename(),
                    claimed.contentType(), claimed.size(), storage.get(claimed.storageKey()));
        }
        catch (ObjectStorageException failure) {
            persistence.recordStorageFailure(claimed, requestId);
            throw new FileStorageUnavailableException(failure);
        }
    }

    private SharedAccess authorize(UUID organisationId, UUID actorId, UUID patientId,
            UUID fileId, String token, String requestId) {
        try {
            var snapshot = persistence.findShareCandidate(fileId, organisationId, patientId);
            Instant validUntil = sharing.requireGrant(snapshot.patientRegistrationId(),
                    "MEDICAL_DOCUMENT", snapshot.fileId(), snapshot.uploaderUserId(),
                    null, token, requestId);
            return new SharedAccess(snapshot, validUntil);
        }
        catch (FileResourceNotFoundException | SharingContextUnavailableException denied) {
            persistence.recordSharedDenied(organisationId, actorId, fileId,
                    denied instanceof SharingContextUnavailableException
                            ? "SHARING_UNAVAILABLE" : "SHARE_OR_OWNERSHIP_DENIED", requestId);
            throw denied;
        }
    }

    private record SharedAccess(MedicalFileAccessSnapshot snapshot, Instant validUntil) { }
}
