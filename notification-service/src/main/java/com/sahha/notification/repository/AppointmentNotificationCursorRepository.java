package com.sahha.notification.repository;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.notification.entity.AppointmentNotificationCursor;

public interface AppointmentNotificationCursorRepository
		extends JpaRepository<AppointmentNotificationCursor, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select cursor
			from AppointmentNotificationCursor cursor
			where cursor.appointmentId = :appointmentId
			""")
	Optional<AppointmentNotificationCursor> findByIdForUpdate(
			@Param("appointmentId") UUID appointmentId);
}
