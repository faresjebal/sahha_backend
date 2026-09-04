package com.sahha.notification.repository;

import java.util.List;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.notification.entity.InAppNotification;

public interface InAppNotificationRepository
		extends JpaRepository<InAppNotification, UUID> {

	List<InAppNotification> findAllByRecipientUserIdOrderByCreatedAtDesc(
			UUID recipientUserId);

	Page<InAppNotification> findAllByOrganisationIdAndRecipientUserId(
			UUID organisationId,
			UUID recipientUserId,
			Pageable pageable);

	Optional<InAppNotification> findByIdAndOrganisationIdAndRecipientUserId(
			UUID notificationId,
			UUID organisationId,
			UUID recipientUserId);

	long countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
			UUID organisationId,
			UUID recipientUserId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select notification
			from InAppNotification notification
			where notification.id = :notificationId
			  and notification.organisationId = :organisationId
			  and notification.recipientUserId = :recipientUserId
			""")
	Optional<InAppNotification> findOwnedForUpdate(
			@Param("notificationId") UUID notificationId,
			@Param("organisationId") UUID organisationId,
			@Param("recipientUserId") UUID recipientUserId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update InAppNotification notification
			set notification.readAt = :readAt,
				notification.version = notification.version + 1
			where notification.organisationId = :organisationId
			  and notification.recipientUserId = :recipientUserId
			  and notification.readAt is null
			""")
	int markAllUnreadAsRead(
			@Param("organisationId") UUID organisationId,
			@Param("recipientUserId") UUID recipientUserId,
			@Param("readAt") Instant readAt);

	long countBySourceEventId(UUID sourceEventId);
}
