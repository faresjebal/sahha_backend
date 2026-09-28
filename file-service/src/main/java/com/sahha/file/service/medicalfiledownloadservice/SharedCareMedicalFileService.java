package com.sahha.file.service.medicalfiledownloadservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import com.sahha.file.client.clinical.ClinicalCareAccessClient;
import com.sahha.file.dto.response.*;
import com.sahha.file.exception.*;
import com.sahha.file.storage.ObjectStorageException;
import com.sahha.file.storage.PrivateObjectStorage;

@Service
public class SharedCareMedicalFileService {
    private final MedicalFileDownloadPersistenceService persistence;
    private final ClinicalCareAccessClient clinical;
    private final PrivateObjectStorage storage;
    private final Clock clock;
    public SharedCareMedicalFileService(MedicalFileDownloadPersistenceService persistence,
            ClinicalCareAccessClient clinical, PrivateObjectStorage storage, Clock clock) {
        this.persistence = persistence; this.clinical = clinical; this.storage = storage; this.clock = clock;
    }

    public SharedCareFilePageResponse list(UUID org, UUID actor, UUID patient, UUID consultation,
            String token, String requestId, int page, int size) {
        if (page < 0 || size < 1 || size > 50) throw new IllegalArgumentException("invalid page");
        try {
            var context = clinical.resolve(org, actor, patient, consultation, token, requestId);
            return persistence.listCare(context, actor, requestId, page, size);
        } catch (FileResourceNotFoundException | ClinicalContextUnavailableException denied) {
            persistence.recordCareListDenied(org, actor, patient, consultation, reason(denied), requestId);
            throw denied;
        }
    }

    public SharedCareFileResponse metadata(UUID org, UUID actor, UUID patient, UUID file, String token, String requestId) {
        return withAccess(org, actor, patient, file, token, requestId, access ->
                new SharedCareFileResponse(org, patient, access.validUntil(),
                        persistence.careMetadata(access.snapshot(), actor, requestId, access.validUntil())));
    }

    public MedicalFileDownloadGrantResponse issue(UUID org, UUID actor, UUID patient, UUID file, String token, String requestId) {
        return withAccess(org, actor, patient, file, token, requestId, access ->
                persistence.issueCare(access.snapshot(), actor, requestId, access.validUntil()));
    }

    public AuthorizedMedicalFileDownload download(UUID org, UUID actor, UUID patient, UUID file,
            String token, String downloadToken, String requestId) {
        return withAccess(org, actor, patient, file, token, requestId, access -> {
            // Issued tokens never replace a fresh Clinical + Communication decision.
            var claimed = persistence.claimCare(access.snapshot(), actor, downloadToken, requestId, access.validUntil());
            try {
                var content = storage.get(claimed.storageKey());
                if (!clock.instant().isBefore(access.validUntil())) {
                    try { content.close(); } catch (java.io.IOException ignored) { /* Never release expired bytes. */ }
                    throw new FileResourceNotFoundException();
                }
                return new AuthorizedMedicalFileDownload(claimed.fileId(), claimed.originalFilename(),
                        claimed.contentType(), claimed.size(), content);
            } catch (ObjectStorageException failure) {
                persistence.recordStorageFailure(claimed, requestId);
                throw new FileStorageUnavailableException(failure);
            }
        });
    }

    private <T> T withAccess(UUID org, UUID actor, UUID patient, UUID file, String token,
            String requestId, Function<Access, T> operation) {
        try {
            var snapshot = persistence.findShareCandidate(file, org, patient);
            var context = clinical.resolve(org, actor, patient, snapshot.consultationId(), token, requestId);
            // File owns the immutable global-patient binding; Clinical owns finality and live care authority.
            if (!snapshot.patientId().equals(context.patientId()) || context.validUntil() == null
                    || !clock.instant().isBefore(context.validUntil())) throw new FileResourceNotFoundException();
            return operation.apply(new Access(snapshot, context.validUntil()));
        } catch (FileResourceNotFoundException | ClinicalContextUnavailableException denied) {
            persistence.recordSharedDenied(org, actor, file, reason(denied), requestId);
            throw denied;
        }
    }
    private String reason(RuntimeException failure) {
        return failure instanceof ClinicalContextUnavailableException ? "CARE_CONTEXT_UNAVAILABLE" : "CARE_OR_OWNERSHIP_DENIED";
    }
    private record Access(MedicalFileAccessSnapshot snapshot, Instant validUntil) { }
}
