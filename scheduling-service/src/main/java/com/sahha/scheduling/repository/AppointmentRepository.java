package com.sahha.scheduling.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.scheduling.entity.Appointment;
import com.sahha.scheduling.entity.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

	Optional<Appointment> findByOrganisationIdAndBookingRequestId(
			UUID organisationId,
			UUID bookingRequestId);

	long countByOrganisationId(UUID organisationId);

	Optional<Appointment> findByIdAndOrganisationId(
			UUID id,
			UUID organisationId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select appointment
			from Appointment appointment
			where appointment.id = :appointmentId
			  and appointment.organisationId = :organisationId
			""")
	Optional<Appointment> findForClinicalCompletion(
			@Param("appointmentId") UUID appointmentId,
			@Param("organisationId") UUID organisationId);

	Optional<Appointment>
			findFirstByOrganisationIdAndPatientRegistrationIdAndDoctorUserIdAndStatusInOrderByStartsAtDesc(
					UUID organisationId,
					UUID patientRegistrationId,
					UUID doctorUserId,
					Collection<AppointmentStatus> statuses);

	@Query("""
			select appointment
			from Appointment appointment
			where appointment.organisationId = :organisationId
			  and appointment.startsAt < :rangeEnd
			  and appointment.endsAt > :rangeStart
			order by appointment.startsAt, appointment.id
			""")
	List<Appointment> findOrganisationAppointments(
			@Param("organisationId") UUID organisationId,
			@Param("rangeStart") Instant rangeStart,
			@Param("rangeEnd") Instant rangeEnd);

	@Query("""
			select appointment
			from Appointment appointment
			where appointment.organisationId = :organisationId
			  and appointment.doctorUserId = :doctorUserId
			  and appointment.startsAt < :rangeEnd
			  and appointment.endsAt > :rangeStart
			order by appointment.startsAt, appointment.id
			""")
	List<Appointment> findDoctorAppointments(
			@Param("organisationId") UUID organisationId,
			@Param("doctorUserId") UUID doctorUserId,
			@Param("rangeStart") Instant rangeStart,
			@Param("rangeEnd") Instant rangeEnd);

	@Query("""
			select appointment
			from Appointment appointment
			where appointment.organisationId = :organisationId
			  and appointment.patientRegistrationId = :patientRegistrationId
			  and appointment.startsAt < :rangeEnd
			  and appointment.endsAt > :rangeStart
			order by appointment.startsAt, appointment.id
			""")
	List<Appointment> findPatientAppointments(
			@Param("organisationId") UUID organisationId,
			@Param("patientRegistrationId") UUID patientRegistrationId,
			@Param("rangeStart") Instant rangeStart,
			@Param("rangeEnd") Instant rangeEnd);

	@Query("""
			select appointment
			from Appointment appointment
			where appointment.organisationId = :organisationId
			  and appointment.doctorUserId = :doctorUserId
			  and appointment.status in :statuses
			  and appointment.startsAt < :rangeEnd
			  and appointment.endsAt > :rangeStart
			order by appointment.startsAt, appointment.id
			""")
	List<Appointment> findBlockingDoctorAppointments(
			@Param("organisationId") UUID organisationId,
			@Param("doctorUserId") UUID doctorUserId,
			@Param("statuses") Collection<AppointmentStatus> statuses,
			@Param("rangeStart") Instant rangeStart,
			@Param("rangeEnd") Instant rangeEnd);
}
