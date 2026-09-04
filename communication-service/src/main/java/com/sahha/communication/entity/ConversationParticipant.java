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

@Entity
@Table(name = "conversation_participant")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConversationParticipant {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "conversation_id", nullable = false, updatable = false)
	private UUID conversationId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;
	@Column(name = "membership_id", nullable = false, updatable = false)
	private UUID membershipId;
	@Column(name = "display_name_snapshot", nullable = false, length = 201)
	private String displayNameSnapshot;
	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;
	@Column(name = "last_read_at")
	private Instant lastReadAt;
	@Column(nullable = false)
	private boolean active;
	@Version @Column(nullable = false)
	private long version;

	public static ConversationParticipant join(UUID conversationId, UUID organisationId,
			UUID userId, UUID membershipId, String displayName, Instant now) {
		ConversationParticipant value = new ConversationParticipant();
		value.id = UUID.randomUUID();
		value.conversationId = Objects.requireNonNull(conversationId);
		value.organisationId = Objects.requireNonNull(organisationId);
		value.userId = Objects.requireNonNull(userId);
		value.membershipId = Objects.requireNonNull(membershipId);
		value.displayNameSnapshot = required(displayName);
		value.joinedAt = Objects.requireNonNull(now);
		value.lastReadAt = now;
		value.active = true;
		return value;
	}

	public boolean markRead(Instant readAt) {
		Instant required = Objects.requireNonNull(readAt);
		if (lastReadAt != null && !required.isAfter(lastReadAt)) return false;
		lastReadAt = required;
		return true;
	}

	private static String required(String value) {
		if (value == null || value.isBlank() || value.strip().length() > 201) {
			throw new IllegalArgumentException("display name is invalid");
		}
		return value.strip().replaceAll("\\s+", " ");
	}
}
