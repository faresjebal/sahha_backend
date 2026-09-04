package com.sahha.notification.service.appointmentnotificationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.entity.AppointmentNotificationCursor;
import com.sahha.notification.entity.NotificationType;
import com.sahha.notification.entity.ConsumedAppointmentEvent;
import com.sahha.notification.entity.ConsumedEventOutcome;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.InAppNotificationCreatedEvent;
import com.sahha.notification.repository.AppointmentNotificationCursorRepository;
import com.sahha.notification.repository.ConsumedAppointmentEventRepository;
import com.sahha.notification.repository.InAppNotificationRepository;

@Service
public class AppointmentNotificationService {

	private final ConsumedAppointmentEventRepository consumedEventRepository;
	private final AppointmentNotificationCursorRepository cursorRepository;
	private final InAppNotificationRepository notificationRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final Clock clock;

	public AppointmentNotificationService(
			ConsumedAppointmentEventRepository consumedEventRepository,
			AppointmentNotificationCursorRepository cursorRepository,
			InAppNotificationRepository notificationRepository,
			ApplicationEventPublisher eventPublisher,
			Clock clock) {
		this.consumedEventRepository = consumedEventRepository;
		this.cursorRepository = cursorRepository;
		this.notificationRepository = notificationRepository;
		this.eventPublisher = eventPublisher;
		this.clock = clock;
	}

	@Transactional
	public AppointmentEventProcessingResult consume(
			AppointmentEventV1 event,
			AppointmentEventSource source) {
		if (consumedEventRepository.existsById(event.eventId())) {
			return AppointmentEventProcessingResult.DUPLICATE;
		}
		Instant processedAt = clock.instant();
		ConsumedAppointmentEvent consumed =
				consumedEventRepository.saveAndFlush(
						ConsumedAppointmentEvent.processing(
								event, source, processedAt));

		AppointmentNotificationCursor cursor = cursorRepository
				.findByIdForUpdate(event.appointmentId())
				.orElse(null);
		if (cursor == null) {
			cursorRepository.save(AppointmentNotificationCursor.initial(
					event, processedAt));
		}
		else if (!cursor.accepts(event)) {
			consumed.complete(ConsumedEventOutcome.STALE, processedAt);
			return AppointmentEventProcessingResult.STALE;
		}
		else {
			cursor.advance(event, processedAt);
		}

		Optional<NotificationType> notificationType =
				doctorNotificationType(event);
		if (notificationType.isEmpty()) {
			consumed.complete(
					ConsumedEventOutcome.NO_ELIGIBLE_RECIPIENT, processedAt);
			return AppointmentEventProcessingResult.NO_ELIGIBLE_RECIPIENT;
		}

		InAppNotification notification = notificationRepository.save(
				InAppNotification.forDoctor(
						event, notificationType.orElseThrow(), processedAt));
		eventPublisher.publishEvent(
				InAppNotificationCreatedEvent.from(notification));
		consumed.complete(
				ConsumedEventOutcome.NOTIFICATION_CREATED, processedAt);
		return AppointmentEventProcessingResult.NOTIFICATION_CREATED;
	}

	private static Optional<NotificationType> doctorNotificationType(
			AppointmentEventV1 event) {
		if (event.doctorUserId().equals(event.actorUserId())) {
			return Optional.empty();
		}
		return NotificationType.forDoctor(event.eventType());
	}
}
