package com.sahha.notification.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.sahha.notification.event.AppointmentEventSource;

@Entity
@Table(name = "rejected_appointment_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class RejectedAppointmentEvent {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "source_topic", nullable = false, length = 249,
			updatable = false)
	private String sourceTopic;

	@Column(name = "source_partition", nullable = false, updatable = false)
	private int sourcePartition;

	@Column(name = "source_offset", nullable = false, updatable = false)
	private long sourceOffset;

	@Column(name = "payload_sha256", nullable = false, length = 64,
			updatable = false)
	private String payloadSha256;

	@Column(name = "reason_code", nullable = false, length = 64,
			updatable = false)
	@ToString.Include
	private String reasonCode;

	@Column(name = "rejected_at", nullable = false, updatable = false)
	private Instant rejectedAt;

	public static RejectedAppointmentEvent record(
			AppointmentEventSource source,
			String payloadSha256,
			String reasonCode,
			Instant rejectedAt) {
		RejectedAppointmentEvent rejected = new RejectedAppointmentEvent();
		rejected.id = UUID.randomUUID();
		rejected.sourceTopic = Objects.requireNonNull(source.topic());
		rejected.sourcePartition = source.partition();
		rejected.sourceOffset = source.offset();
		rejected.payloadSha256 = required(
				payloadSha256, 64, "payloadSha256");
		rejected.reasonCode = required(reasonCode, 64, "reasonCode");
		rejected.rejectedAt = Objects.requireNonNull(rejectedAt);
		return rejected;
	}

	private static String required(
			String value,
			int expectedMaximum,
			String fieldName) {
		Objects.requireNonNull(value);
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > expectedMaximum) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
