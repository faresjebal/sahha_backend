package com.sahha.clinical.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.clinical.entity.Consultation;
import com.sahha.clinical.entity.ConsultationStatus;

public interface ConsultationRepository extends JpaRepository<Consultation, UUID> {

	Page<Consultation> findAllByOrganisationIdAndPatientRegistrationIdAndStatusOrderByFinalizedAtDescIdDesc(
			UUID organisationId, UUID patientRegistrationId, ConsultationStatus status, Pageable pageable);

	Page<Consultation>
			findByOrganisationIdAndDoctorUserIdAndDoctorMembershipIdAndStatusOrderByFinalizedAtDescIdDesc(
					UUID organisationId, UUID doctorUserId, UUID doctorMembershipId,
					ConsultationStatus status, Pageable pageable);

	@Query("""
			select c from Consultation c
			where c.organisationId = :organisationId
			  and c.patientRegistrationId = :patientRegistrationId
			  and c.status = com.sahha.clinical.entity.ConsultationStatus.FINALIZED
			  and ((:resourceType = 'CONSULTATION' and c.id = :resourceId)
			    or (:resourceType = 'DIAGNOSIS' and exists
			      (select d.id from ClinicalDiagnosis d where d.consultationId = c.id and d.id = :resourceId))
			    or (:resourceType = 'MEDICATION' and exists
			      (select m.id from ClinicalMedicationItem m where m.consultationId = c.id and m.id = :resourceId))
			    or (:resourceType = 'ALLERGY' and exists
			      (select h.id from ClinicalHistoryEntry h where h.consultationId = c.id and h.id = :resourceId)))
			""")
	Optional<Consultation> findSharedResourceOwner(
			@Param("organisationId") UUID organisationId,
			@Param("patientRegistrationId") UUID patientRegistrationId,
			@Param("resourceType") String resourceType,
			@Param("resourceId") UUID resourceId);

	@Modifying
	@Query(value = """
			insert into clinical_consultation (
			    id, organisation_id, appointment_id, patient_registration_id,
			    patient_id, doctor_user_id, doctor_membership_id, status,
			    created_at, updated_at, version
			) values (
			    :id, :organisationId, :appointmentId, :patientRegistrationId,
			    :patientId, :doctorUserId, :doctorMembershipId, 'DRAFT',
			    :createdAt, :createdAt, 0
			)
			on conflict (appointment_id) do nothing
			""", nativeQuery = true)
	int insertDraftIfAbsent(
			@Param("id") UUID id,
			@Param("organisationId") UUID organisationId,
			@Param("appointmentId") UUID appointmentId,
			@Param("patientRegistrationId") UUID patientRegistrationId,
			@Param("patientId") UUID patientId,
			@Param("doctorUserId") UUID doctorUserId,
			@Param("doctorMembershipId") UUID doctorMembershipId,
			@Param("createdAt") java.time.Instant createdAt);

	Optional<Consultation> findByOrganisationIdAndAppointmentId(
			UUID organisationId,
			UUID appointmentId);

	Optional<Consultation> findByIdAndOrganisationIdAndDoctorUserId(
			UUID id,
			UUID organisationId,
			UUID doctorUserId);

	List<Consultation>
			findByOrganisationIdAndPatientRegistrationIdAndStatusOrderByFinalizedAtDescIdDesc(
					UUID organisationId,
					UUID patientRegistrationId,
					ConsultationStatus status,
					Pageable pageable);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select consultation
			from Consultation consultation
			where consultation.id = :id
			  and consultation.organisationId = :organisationId
			  and consultation.doctorUserId = :doctorUserId
			""")
	Optional<Consultation> findOwnedDraftForUpdate(
			@Param("id") UUID id,
			@Param("organisationId") UUID organisationId,
			@Param("doctorUserId") UUID doctorUserId);
}
