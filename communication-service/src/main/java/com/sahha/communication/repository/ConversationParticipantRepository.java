package com.sahha.communication.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.communication.entity.ConversationParticipant;

public interface ConversationParticipantRepository
		extends JpaRepository<ConversationParticipant, UUID> {
	List<ConversationParticipant> findAllByConversationIdAndOrganisationIdAndActiveTrueOrderByJoinedAt(
			UUID conversationId, UUID organisationId);
	Optional<ConversationParticipant> findByConversationIdAndOrganisationIdAndUserIdAndActiveTrue(
			UUID conversationId, UUID organisationId, UUID userId);
}
