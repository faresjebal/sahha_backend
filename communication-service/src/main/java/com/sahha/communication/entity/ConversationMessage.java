package com.sahha.communication.entity;

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

@Entity
@Table(name = "conversation_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationMessage {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "message_request_id", nullable = false, updatable = false)
	private UUID messageRequestId;
	@Column(name = "sender_user_id", nullable = false, updatable = false)
	private UUID senderUserId;
	@Column(name = "sender_membership_id", nullable = false, updatable = false)
	private UUID senderMembershipId;
	@Column(name = "sender_display_name_snapshot", nullable = false, length = 201, updatable = false)
	private String senderDisplayNameSnapshot;
	@Column(nullable = false, length = 4000, updatable = false)
	private String body;
	@Column(name = "sent_at", nullable = false, updatable = false)
	private Instant sentAt;

	public static ConversationMessage send(UUID conversationId, UUID organisationId,
			UUID requestId, UUID senderUserId, UUID membershipId,
			String displayName, String body, Instant sentAt) {
		ConversationMessage value = new ConversationMessage();
		value.id = UUID.randomUUID();
		value.conversationId = Objects.requireNonNull(conversationId);
		value.organisationId = Objects.requireNonNull(organisationId);
		value.messageRequestId = Objects.requireNonNull(requestId);
		value.senderUserId = Objects.requireNonNull(senderUserId);
		value.senderMembershipId = Objects.requireNonNull(membershipId);
		value.senderDisplayNameSnapshot = required(displayName, 201, "sender");
		value.body = required(body, 4000, "body");
		value.sentAt = Objects.requireNonNull(sentAt);
		return value;
	}

	private static String required(String value, int maximum, String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
		String normalized = value.strip();
		if (normalized.length() > maximum) throw new IllegalArgumentException(field + " is too long");
		return normalized;
	}
}
