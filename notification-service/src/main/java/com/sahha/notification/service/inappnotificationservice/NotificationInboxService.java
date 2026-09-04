package com.sahha.notification.service.inappnotificationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.dto.response.MarkAllNotificationsReadResponse;
import com.sahha.notification.dto.response.NotificationPageResponse;
import com.sahha.notification.dto.response.NotificationResponse;
import com.sahha.notification.dto.response.UnreadNotificationCountResponse;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.exception.NotificationNotFoundException;
import com.sahha.notification.mapper.NotificationMapper;
import com.sahha.notification.repository.InAppNotificationRepository;

@Service
public class NotificationInboxService {

	private static final Sort NEWEST_FIRST = Sort.by(
			Sort.Order.desc("createdAt"),
			Sort.Order.desc("id"));
	private final InAppNotificationRepository notificationRepository;
	private final NotificationAccessService accessService;
	private final NotificationMapper mapper;
	private final Clock clock;

	public NotificationInboxService(
			InAppNotificationRepository notificationRepository,
			NotificationAccessService accessService,
			NotificationMapper mapper,
			Clock clock) {
		this.notificationRepository = notificationRepository;
		this.accessService = accessService;
		this.mapper = mapper;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public NotificationPageResponse list(
			UUID organisationId,
			UUID recipientUserId,
			String accessToken,
			int page,
			int size) {
		accessService.requireActiveMembership(
				organisationId, recipientUserId, accessToken);
		Page<InAppNotification> notifications = notificationRepository
				.findAllByOrganisationIdAndRecipientUserId(
						organisationId,
						recipientUserId,
						PageRequest.of(page, size, NEWEST_FIRST));
		return mapper.toPage(notifications);
	}

	@Transactional(readOnly = true)
	public UnreadNotificationCountResponse unreadCount(
			UUID organisationId,
			UUID recipientUserId,
			String accessToken) {
		accessService.requireActiveMembership(
				organisationId, recipientUserId, accessToken);
		return new UnreadNotificationCountResponse(
				notificationRepository
						.countByOrganisationIdAndRecipientUserIdAndReadAtIsNull(
								organisationId, recipientUserId));
	}

	@Transactional
	public NotificationResponse markRead(
			UUID organisationId,
			UUID recipientUserId,
			String accessToken,
			UUID notificationId) {
		accessService.requireActiveMembership(
				organisationId, recipientUserId, accessToken);
		InAppNotification notification = notificationRepository
				.findOwnedForUpdate(
						notificationId, organisationId, recipientUserId)
				.orElseThrow(NotificationNotFoundException::new);
		if (notification.getReadAt() == null) {
			notification.markRead(clock.instant());
		}
		return mapper.toResponse(notification);
	}

	@Transactional
	public MarkAllNotificationsReadResponse markAllRead(
			UUID organisationId,
			UUID recipientUserId,
			String accessToken) {
		accessService.requireActiveMembership(
				organisationId, recipientUserId, accessToken);
		Instant readAt = clock.instant();
		int updated = notificationRepository.markAllUnreadAsRead(
				organisationId, recipientUserId, readAt);
		return new MarkAllNotificationsReadResponse(updated, readAt);
	}
}
