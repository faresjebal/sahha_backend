package com.sahha.file.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sahha.file.entity.FileDownloadGrant;

public interface FileDownloadGrantRepository
		extends JpaRepository<FileDownloadGrant, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<FileDownloadGrant> findForUpdateByTokenDigest(String tokenDigest);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<FileDownloadGrant> findForUpdateByMedicalFileIdAndOrganisationIdAndActorUserIdAndTokenDigest(
			UUID medicalFileId,
			UUID organisationId,
			UUID actorUserId,
			String tokenDigest);
}
