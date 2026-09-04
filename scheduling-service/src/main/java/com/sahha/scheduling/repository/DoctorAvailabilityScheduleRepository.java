package com.sahha.scheduling.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.scheduling.entity.DoctorAvailabilitySchedule;

public interface DoctorAvailabilityScheduleRepository
		extends JpaRepository<DoctorAvailabilitySchedule, UUID> {

	Optional<DoctorAvailabilitySchedule>
			findByOrganisationIdAndDoctorUserId(
					UUID organisationId,
					UUID doctorUserId);

	List<DoctorAvailabilitySchedule> findAllByOrganisationIdOrderByDoctorUserId(
			UUID organisationId);
}
