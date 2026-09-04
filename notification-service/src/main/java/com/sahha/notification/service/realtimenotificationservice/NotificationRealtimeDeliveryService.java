package com.sahha.notification.service.realtimenotificationservice;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.config.NotificationWebSocketConfiguration;
import com.sahha.notification.dto.response.RealtimeNotificationMessage;
import com.sahha.notification.event.InAppNotificationCreatedEvent;
import com.sahha.notification.mapper.NotificationMapper;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.security.NotificationPrincipalName;

@Service
public class NotificationRealtimeDeliveryService {

	private final InAppNotificationRepository notificationRepository;
	private final NotificationMapper mapper;
	private final SimpMessagingTemplate messagingTemplate;

	public NotificationRealtimeDeliveryService(
			InAppNotificationRepository notificationRepository,
			NotificationMapper mapper,
			SimpMessagingTemplate messagingTemplate) {
		this.notificationRepository = notificationRepository;
		this.mapper = mapper;
		this.messagingTemplate = messagingTemplate;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public boolean deliver(InAppNotificationCreatedEvent event) {
		return notificationRepository
				.findByIdAndOrganisationIdAndRecipientUserId(
						event.notificationId(),
						event.organisationId(),
						event.recipientUserId())
				.map(notification -> {
					messagingTemplate.convertAndSendToUser(
							NotificationPrincipalName.of(
									event.recipientUserId(),
									event.organisationId()),
							NotificationWebSocketConfiguration.DELIVERY_DESTINATION,
							RealtimeNotificationMessage.created(
									mapper.toResponse(notification)));
					return true;
				})
				.orElse(false);
	}
}
