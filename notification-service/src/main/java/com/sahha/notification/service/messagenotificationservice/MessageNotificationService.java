package com.sahha.notification.service.messagenotificationservice;

import java.time.Clock;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.entity.ConsumedCommunicationEvent;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.CommunicationEventV1;
import com.sahha.notification.event.InAppNotificationCreatedEvent;
import com.sahha.notification.repository.ConsumedCommunicationEventRepository;
import com.sahha.notification.repository.InAppNotificationRepository;

@Service
public class MessageNotificationService {
	private final ConsumedCommunicationEventRepository consumedRepository;
	private final InAppNotificationRepository notificationRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public MessageNotificationService(
			ConsumedCommunicationEventRepository consumedRepository,
			InAppNotificationRepository notificationRepository,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.consumedRepository = consumedRepository;
		this.notificationRepository = notificationRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public CommunicationEventProcessingResult consume(
			CommunicationEventV1 event,
			CommunicationEventSource source) {
		if (consumedRepository.existsById(event.eventId())) {
			return CommunicationEventProcessingResult.DUPLICATE;
		}
		var processedAt = clock.instant();
		var consumed = consumedRepository.saveAndFlush(
				ConsumedCommunicationEvent.processing(event, source, processedAt));
		for (var recipient : event.recipientUserIds()) {
			InAppNotification notification = notificationRepository.save(
					InAppNotification.forMessageRecipient(event, recipient, processedAt));
			eventPublisher.publishEvent(InAppNotificationCreatedEvent.from(notification));
		}
		consumed.complete(processedAt);
		return CommunicationEventProcessingResult.NOTIFICATIONS_CREATED;
	}
}
