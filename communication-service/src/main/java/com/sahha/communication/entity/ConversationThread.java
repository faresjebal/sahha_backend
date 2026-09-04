package com.sahha.communication.entity;

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

@Entity
@Table(name = "conversation_thread")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class ConversationThread {
	@Id @Column(nullable = false, updatable = false) @ToString.Include
	private UUID id;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "creation_request_id", nullable = false, updatable = false)
	private UUID creationRequestId;
	@Column(nullable = false, length = 160)
	private String subject;
	@Column(name = "patient_registration_id", updatable = false)
	private UUID patientRegistrationId;
	@Column(name = "created_by_user_id", nullable = false, updatable = false)
	private UUID createdByUserId;
	@Column(name = "created_by_membership_id", nullable = false, updatable = false)
	private UUID createdByMembershipId;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
	@Column(name = "last_message_at", nullable = false)
	private Instant lastMessageAt;
	@Version @Column(nullable = false)
	private long version;

	public static ConversationThread create(UUID id, UUID organisationId,
			UUID requestId, String subject, UUID patientRegistrationId,
			UUID actorUserId, UUID actorMembershipId, Instant now) {
		ConversationThread value = new ConversationThread();
		value.id = Objects.requireNonNull(id);
		value.organisationId = Objects.requireNonNull(organisationId);
		value.creationRequestId = Objects.requireNonNull(requestId);
		value.subject = normalized(subject, 4, 160);
		value.patientRegistrationId = patientRegistrationId;
		value.createdByUserId = Objects.requireNonNull(actorUserId);
		value.createdByMembershipId = Objects.requireNonNull(actorMembershipId);
		value.createdAt = Objects.requireNonNull(now);
		value.updatedAt = now;
		value.lastMessageAt = now;
		return value;
	}

	public void messageSent(Instant sentAt) {
		Instant required = Objects.requireNonNull(sentAt);
		if (required.isBefore(createdAt)) throw new IllegalArgumentException("message precedes conversation");
		updatedAt = required;
		lastMessageAt = required;
	}

	private static String normalized(String value, int minimum, int maximum) {
		if (value == null) throw new IllegalArgumentException("subject is required");
		String result = value.strip().replaceAll("\\s+", " ");
		if (result.length() < minimum || result.length() > maximum) {
			throw new IllegalArgumentException("subject is invalid");
		}
		return result;
	}
}
