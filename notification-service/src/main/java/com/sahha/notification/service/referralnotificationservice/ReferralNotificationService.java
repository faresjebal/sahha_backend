package com.sahha.notification.service.referralnotificationservice;

import java.time.Clock;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.notification.entity.InAppNotification;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.event.InAppNotificationCreatedEvent;
import com.sahha.notification.event.ReferralEventV1;
import com.sahha.notification.repository.InAppNotificationRepository;
import com.sahha.notification.repository.ReferralNotificationProjection;

@Service
public class ReferralNotificationService {
    public enum Result { NOTIFICATIONS_CREATED, DUPLICATE, STALE }
    private final ReferralNotificationProjection projection;
    private final InAppNotificationRepository notifications;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    public ReferralNotificationService(ReferralNotificationProjection projection,
            InAppNotificationRepository notifications, ApplicationEventPublisher publisher, Clock clock) {
        this.projection = projection; this.notifications = notifications;
        this.publisher = publisher; this.clock = clock;
    }
    @Transactional
    public Result consume(ReferralEventV1 event, CommunicationEventSource source) {
        var now = clock.instant();
        if (!projection.claim(event, source, now)) return Result.DUPLICATE;
        if (!projection.advance(event)) {
            projection.finish(event, false);
            return Result.STALE;
        }
        for (var recipient : event.recipientUserIds()) {
            var notification = notifications.save(InAppNotification.forReferralRecipient(event, recipient, now));
            publisher.publishEvent(InAppNotificationCreatedEvent.from(notification));
        }
        projection.finish(event, true);
        return Result.NOTIFICATIONS_CREATED;
    }
}
