package com.sahha.patient.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.patient.entity.PatientAccountLink;

public interface PatientAccountLinkRepository
		extends JpaRepository<PatientAccountLink, UUID> {

	Optional<PatientAccountLink> findByAuthUserId(UUID authUserId);

	Optional<PatientAccountLink> findByPatientId(UUID patientId);
}
