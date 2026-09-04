package com.sahha.patient.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.patient.entity.PatientOrganisationRegistration;

public interface PatientOrganisationRegistrationRepository
		extends JpaRepository<PatientOrganisationRegistration, UUID> {

	@EntityGraph(attributePaths = "patient")
	Optional<PatientOrganisationRegistration> findByIdAndOrganisationId(
			UUID id,
			UUID organisationId);

	Optional<PatientOrganisationRegistration>
			findByOrganisationIdAndPatientId(UUID organisationId, UUID patientId);

	@EntityGraph(attributePaths = "patient")
	Optional<PatientOrganisationRegistration>
			findByOrganisationIdAndMedicalRecordNumber(
					UUID organisationId,
					String medicalRecordNumber);

	@EntityGraph(attributePaths = "patient")
	List<PatientOrganisationRegistration> findAllByPatientIdOrderByCreatedAt(
			UUID patientId);

	@EntityGraph(attributePaths = "patient")
	List<PatientOrganisationRegistration> findAllByNormalizedPhoneNumber(
			String normalizedPhoneNumber);

	@EntityGraph(attributePaths = "patient")
	List<PatientOrganisationRegistration> findAllByNormalizedEmail(
			String normalizedEmail);

	List<PatientOrganisationRegistration>
			findAllByOrganisationIdAndPatientIdIn(
					UUID organisationId,
					Collection<UUID> patientIds);

	long countByPatientId(UUID patientId);

	@Query(
			value = """
					select registration
					from PatientOrganisationRegistration registration
					join fetch registration.patient patient
					where registration.organisationId = :organisationId
					  and (
					      :query = ''
					      or patient.normalizedFirstName like concat('%', :query, '%')
					      or patient.normalizedLastName like concat('%', :query, '%')
					      or concat(patient.normalizedFirstName, ' ', patient.normalizedLastName)
					          like concat('%', :query, '%')
					      or lower(registration.medicalRecordNumber)
					          like concat('%', :query, '%')
					      or registration.normalizedPhoneNumber
					          like concat('%', :query, '%')
					      or registration.normalizedEmail
					          like concat('%', :query, '%')
					  )
					""",
			countQuery = """
					select count(registration)
					from PatientOrganisationRegistration registration
					join registration.patient patient
					where registration.organisationId = :organisationId
					  and (
					      :query = ''
					      or patient.normalizedFirstName like concat('%', :query, '%')
					      or patient.normalizedLastName like concat('%', :query, '%')
					      or concat(patient.normalizedFirstName, ' ', patient.normalizedLastName)
					          like concat('%', :query, '%')
					      or lower(registration.medicalRecordNumber)
					          like concat('%', :query, '%')
					      or registration.normalizedPhoneNumber
					          like concat('%', :query, '%')
					      or registration.normalizedEmail
					          like concat('%', :query, '%')
					  )
					""")
	Page<PatientOrganisationRegistration> searchAdministrativeDirectory(
			@Param("organisationId") UUID organisationId,
			@Param("query") String query,
			Pageable pageable);
}
