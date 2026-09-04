package com.sahha.file.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.sahha.file.entity.FileScanStatus;
import com.sahha.file.entity.FileUploadStatus;
import com.sahha.file.entity.MedicalFile;

public interface MedicalFileRepository extends JpaRepository<MedicalFile, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MedicalFile> findForUpdateById(UUID id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<MedicalFile> findForUpdateByIdAndOrganisationIdAndUploaderUserId(
			UUID id, UUID organisationId, UUID uploaderUserId);

	Optional<MedicalFile> findByIdAndOrganisationIdAndUploaderUserId(
			UUID id, UUID organisationId, UUID uploaderUserId);

	List<MedicalFile> findByOrganisationIdAndConsultationIdOrderByCreatedAtDesc(
			UUID organisationId, UUID consultationId);

	List<MedicalFile> findByOrganisationIdAndConsultationIdAndPatientRegistrationIdAndPatientIdAndUploaderUserIdOrderByCreatedAtDesc(
			UUID organisationId,
			UUID consultationId,
			UUID patientRegistrationId,
			UUID patientId,
			UUID uploaderUserId);

	List<MedicalFile> findByUploadStatusAndScanStatusOrderByUploadedAt(
			FileUploadStatus uploadStatus, FileScanStatus scanStatus);
}
