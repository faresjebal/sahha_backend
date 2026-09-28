package com.sahha.notification.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.sahha.notification.event.AppointmentEventV1;
import com.sahha.notification.event.AppointmentStatus;
import com.sahha.notification.event.CommunicationEventV1;

@Entity
@Table(name = "in_app_notification")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class InAppNotification {

	private static final String APPOINTMENT_RESOURCE = "APPOINTMENT";

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "source_event_id", nullable = false, updatable = false)
	private UUID sourceEventId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "recipient_user_id", nullable = false, updatable = false)
	private UUID recipientUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "notification_type", nullable = false, length = 48,
			updatable = false)
	@ToString.Include
	private NotificationType notificationType;

	@Column(name = "resource_type", nullable = false, length = 32,
			updatable = false)
	private String resourceType;

	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "appointment_status", length = 24,
			updatable = false)
	private AppointmentStatus appointmentStatus;

	@Column(name = "appointment_starts_at", updatable = false)
	private Instant appointmentStartsAt;

	@Column(name = "appointment_ends_at", updatable = false)
	private Instant appointmentEndsAt;

	@Column(name = "appointment_time_zone", length = 64,
			updatable = false)
	private String appointmentTimeZone;

	@Column(name = "appointment_location_label", length = 160,
			updatable = false)
	private String appointmentLocationLabel;

	@Column(name = "resource_version", nullable = false, updatable = false)
	private long resourceVersion;

	@Column(name = "event_occurred_at", nullable = false, updatable = false)
	private Instant eventOccurredAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "read_at")
	private Instant readAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static InAppNotification forDoctor(
			AppointmentEventV1 event,
			NotificationType type,
			Instant createdAt) {
		InAppNotification notification = new InAppNotification();
		notification.id = UUID.randomUUID();
		notification.sourceEventId = Objects.requireNonNull(event.eventId());
		notification.organisationId = Objects.requireNonNull(
				event.organisationId());
		notification.recipientUserId = Objects.requireNonNull(
				event.doctorUserId());
		notification.notificationType = Objects.requireNonNull(type);
		notification.resourceType = APPOINTMENT_RESOURCE;
		notification.resourceId = Objects.requireNonNull(event.appointmentId());
		notification.appointmentStatus = Objects.requireNonNull(event.status());
		notification.appointmentStartsAt = Objects.requireNonNull(event.startsAt());
		notification.appointmentEndsAt = Objects.requireNonNull(event.endsAt());
		notification.appointmentTimeZone = Objects.requireNonNull(event.timeZone());
		notification.appointmentLocationLabel = Objects.requireNonNull(
				event.locationLabel());
		notification.resourceVersion = Objects.requireNonNull(
				event.resourceVersion());
		notification.eventOccurredAt = Objects.requireNonNull(event.occurredAt());
		notification.createdAt = Objects.requireNonNull(createdAt);
		return notification;
	}

	public static InAppNotification forMessageRecipient(
			CommunicationEventV1 event,
			UUID recipientUserId,
			Instant createdAt) {
		InAppNotification notification = new InAppNotification();
		notification.id = UUID.randomUUID();
		notification.sourceEventId = Objects.requireNonNull(event.eventId());
		notification.organisationId = Objects.requireNonNull(event.organisationId());
		notification.recipientUserId = Objects.requireNonNull(recipientUserId);
		notification.notificationType = NotificationType.MESSAGE_RECEIVED;
		notification.resourceType = "CONVERSATION";
		notification.resourceId = Objects.requireNonNull(event.conversationId());
		notification.resourceVersion = Objects.requireNonNull(event.resourceVersion());
		notification.eventOccurredAt = Objects.requireNonNull(event.occurredAt());
		notification.createdAt = Objects.requireNonNull(createdAt);
		return notification;
	}

	public static InAppNotification forReferralRecipient(
			com.sahha.notification.event.ReferralEventV1 event,
			UUID recipientUserId, Instant createdAt) {
		InAppNotification notification = new InAppNotification();
		notification.id = UUID.randomUUID();
		notification.sourceEventId = Objects.requireNonNull(event.eventId());
		notification.organisationId = Objects.requireNonNull(event.organisationId());
		notification.recipientUserId = Objects.requireNonNull(recipientUserId);
		notification.notificationType = event.notificationType();
		notification.resourceType = "REFERRAL";
		notification.resourceId = Objects.requireNonNull(event.referralId());
		notification.resourceVersion = Objects.requireNonNull(event.resourceVersion());
		notification.eventOccurredAt = Objects.requireNonNull(event.occurredAt());
		notification.createdAt = Objects.requireNonNull(createdAt);
		return notification;
	}

	public void markRead(Instant readAt) {
		Instant requiredReadAt = Objects.requireNonNull(readAt);
		if (requiredReadAt.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					"read time must not precede notification creation");
		}
		this.readAt = requiredReadAt;
	}
}
