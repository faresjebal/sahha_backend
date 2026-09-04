package com.sahha.communication.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sahha.communication.entity.ConversationThread;

public interface ConversationThreadRepository extends JpaRepository<ConversationThread, UUID> {
	Optional<ConversationThread> findByOrganisationIdAndCreatedByUserIdAndCreationRequestId(
			UUID organisationId, UUID createdByUserId, UUID creationRequestId);

	@Query("""
			select thread from ConversationThread thread, ConversationParticipant participant
			where participant.conversationId = thread.id
			  and thread.organisationId = :organisationId
			  and participant.organisationId = :organisationId
			  and participant.userId = :userId
			  and participant.active = true
			order by thread.lastMessageAt desc, thread.id desc
			""")
	Page<ConversationThread> findParticipantThreads(
			@Param("organisationId") UUID organisationId,
			@Param("userId") UUID userId,
			Pageable pageable);

	@Query("""
			select thread from ConversationThread thread, ConversationParticipant participant
			where thread.id = :conversationId
			  and thread.organisationId = :organisationId
			  and participant.conversationId = thread.id
			  and participant.organisationId = :organisationId
			  and participant.userId = :userId
			  and participant.active = true
			""")
	Optional<ConversationThread> findParticipantThread(
			@Param("conversationId") UUID conversationId,
			@Param("organisationId") UUID organisationId,
			@Param("userId") UUID userId);
}
