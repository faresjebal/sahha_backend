package com.sahha.file.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.file.entity.FileAuditEvent;

public interface FileAuditEventRepository extends JpaRepository<FileAuditEvent, UUID> {

	long countByMedicalFileId(UUID medicalFileId);
}
