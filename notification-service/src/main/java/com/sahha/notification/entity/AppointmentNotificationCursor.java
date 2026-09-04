package com.sahha.notification.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.sahha.notification.event.AppointmentEventV1;

@Entity
@Table(name = "appointment_notification_cursor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class AppointmentNotificationCursor {

	@Id
	@Column(name = "appointment_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID appointmentId;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;

	@Column(name = "last_event_id", nullable = false)
	private UUID lastEventId;

	@Column(name = "last_resource_version", nullable = false)
	@ToString.Include
	private long lastResourceVersion;

	@Column(name = "last_event_occurred_at", nullable = false)
	private Instant lastEventOccurredAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static AppointmentNotificationCursor initial(
			AppointmentEventV1 event,
			Instant processedAt) {
		AppointmentNotificationCursor cursor =
				new AppointmentNotificationCursor();
		cursor.appointmentId = Objects.requireNonNull(event.appointmentId());
		cursor.organisationId = Objects.requireNonNull(event.organisationId());
		cursor.apply(event, processedAt);
		return cursor;
	}

	public boolean accepts(AppointmentEventV1 event) {
		if (!organisationId.equals(event.organisationId())) {
			throw new IllegalArgumentException(
					"appointment organisation cannot change");
		}
		return event.resourceVersion() > lastResourceVersion;
	}

	public void advance(AppointmentEventV1 event, Instant processedAt) {
		if (!accepts(event)) {
			throw new IllegalArgumentException(
					"appointment event version must advance");
		}
		apply(event, processedAt);
	}

	private void apply(AppointmentEventV1 event, Instant processedAt) {
		lastEventId = Objects.requireNonNull(event.eventId());
		lastResourceVersion = Objects.requireNonNull(event.resourceVersion());
		lastEventOccurredAt = Objects.requireNonNull(event.occurredAt());
		updatedAt = Objects.requireNonNull(processedAt);
	}
}
