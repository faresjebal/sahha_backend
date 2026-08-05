package com.sahha.patient.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.patient.entity.PatientIdentifierType;
import com.sahha.patient.entity.PatientIdentity;

public interface PatientIdentityRepository
		extends JpaRepository<PatientIdentity, UUID> {

	Optional<PatientIdentity> findByIdentifierTypeAndIdentifierFingerprint(
			PatientIdentifierType identifierType,
			String identifierFingerprint);

	List<PatientIdentity>
			findAllByNormalizedFirstNameAndNormalizedLastNameAndDateOfBirth(
					String normalizedFirstName,
					String normalizedLastName,
					LocalDate dateOfBirth);
}
