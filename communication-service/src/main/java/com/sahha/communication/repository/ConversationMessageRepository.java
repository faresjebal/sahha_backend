package com.sahha.communication.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sahha.communication.entity.ConversationMessage;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {
	Optional<ConversationMessage> findByConversationIdAndSenderUserIdAndMessageRequestId(
			UUID conversationId, UUID senderUserId, UUID messageRequestId);
	Page<ConversationMessage> findAllByConversationIdAndOrganisationIdOrderBySentAtDescIdDesc(
			UUID conversationId, UUID organisationId, Pageable pageable);
	long countByConversationIdAndOrganisationIdAndSenderUserIdNotAndSentAtAfter(
			UUID conversationId, UUID organisationId, UUID senderUserId, java.time.Instant sentAt);
}
