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

import com.sahha.notification.event.CommunicationEventSource;

@Entity
@Table(name = "rejected_communication_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RejectedCommunicationEvent {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "source_topic", nullable = false, length = 249, updatable = false)
	private String sourceTopic;
	@Column(name = "source_partition", nullable = false, updatable = false)
	private int sourcePartition;
	@Column(name = "source_offset", nullable = false, updatable = false)
	private long sourceOffset;
	@Column(name = "payload_sha256", nullable = false, length = 64, updatable = false)
	private String payloadSha256;
	@Column(name = "reason_code", nullable = false, length = 64, updatable = false)
	private String reasonCode;
	@Column(name = "rejected_at", nullable = false, updatable = false)
	private Instant rejectedAt;

	public static RejectedCommunicationEvent record(CommunicationEventSource source,
			String payloadSha256, String reasonCode, Instant rejectedAt) {
		RejectedCommunicationEvent value = new RejectedCommunicationEvent();
		value.id = UUID.randomUUID();
		value.sourceTopic = Objects.requireNonNull(source.topic());
		value.sourcePartition = source.partition();
		value.sourceOffset = source.offset();
		value.payloadSha256 = required(payloadSha256, 64);
		value.reasonCode = required(reasonCode, 64);
		value.rejectedAt = Objects.requireNonNull(rejectedAt);
		return value;
	}

	private static String required(String value, int maximum) {
		String stripped = Objects.requireNonNull(value).strip();
		if (stripped.isEmpty() || stripped.length() > maximum) {
			throw new IllegalArgumentException("rejected event value is invalid");
		}
		return stripped;
	}
}
