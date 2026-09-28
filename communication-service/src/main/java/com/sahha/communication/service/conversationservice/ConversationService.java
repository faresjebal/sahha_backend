package com.sahha.communication.service.conversationservice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.config.CommunicationOutboxProperties;
import com.sahha.communication.dto.request.CreateConversationRequest;
import com.sahha.communication.dto.request.SendMessageRequest;
import com.sahha.communication.dto.response.ConversationPageResponse;
import com.sahha.communication.dto.response.ConversationParticipantResponse;
import com.sahha.communication.dto.response.ConversationResponse;
import com.sahha.communication.dto.response.MessagePageResponse;
import com.sahha.communication.dto.response.MessageResponse;
import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.entity.CommunicationOutboxEvent;
import com.sahha.communication.entity.ConversationMessage;
import com.sahha.communication.entity.ConversationParticipant;
import com.sahha.communication.entity.ConversationThread;
import com.sahha.communication.event.CommunicationEventMapper;
import com.sahha.communication.event.CommunicationMessageCreatedEvent;
import com.sahha.communication.exception.ConversationConflictException;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;
import com.sahha.communication.repository.ConversationMessageRepository;
import com.sahha.communication.repository.ConversationParticipantRepository;
import com.sahha.communication.repository.ConversationThreadRepository;

@Service
public class ConversationService {
	private final ConversationThreadRepository threadRepository;
	private final ConversationParticipantRepository participantRepository;
	private final ConversationMessageRepository messageRepository;
	private final CommunicationAuditEventRepository auditRepository;
	private final CommunicationOutboxEventRepository outboxRepository;
	private final OrganisationCollaborationClient organisationClient;
	private final CollaborationPatientContextService patientContextClient;
	private final CommunicationEventMapper eventMapper;
	private final ApplicationEventPublisher eventPublisher;
	private final CommunicationOutboxProperties outboxProperties;
	private final Clock clock;
    private final com.sahha.communication.attachment.MessageAttachmentReferences attachments;

	@Autowired
	public ConversationService(ConversationThreadRepository threadRepository,
			ConversationParticipantRepository participantRepository,
			ConversationMessageRepository messageRepository,
			CommunicationAuditEventRepository auditRepository,
			CommunicationOutboxEventRepository outboxRepository,
			OrganisationCollaborationClient organisationClient,
			CollaborationPatientContextService patientContextClient,
			CommunicationEventMapper eventMapper, ApplicationEventPublisher eventPublisher,
			CommunicationOutboxProperties outboxProperties,
			Clock clock, com.sahha.communication.attachment.MessageAttachmentReferences attachments) {
		this.threadRepository = threadRepository;
		this.participantRepository = participantRepository;
		this.messageRepository = messageRepository;
		this.auditRepository = auditRepository;
		this.outboxRepository = outboxRepository;
		this.organisationClient = organisationClient;
		this.patientContextClient = patientContextClient;
		this.eventMapper = eventMapper;
		this.eventPublisher = eventPublisher;
		this.outboxProperties = outboxProperties;
		this.clock = clock;
        this.attachments = attachments;
	}

	/** Backwards-compatible constructor for unit tests that do not need realtime delivery. */
	public ConversationService(ConversationThreadRepository threadRepository,
			ConversationParticipantRepository participantRepository,
			ConversationMessageRepository messageRepository,
			CommunicationAuditEventRepository auditRepository,
			CommunicationOutboxEventRepository outboxRepository,
			OrganisationCollaborationClient organisationClient,
			CollaborationPatientContextService patientContextClient,
			CommunicationEventMapper eventMapper, Clock clock) {
		this(threadRepository, participantRepository, messageRepository, auditRepository,
				outboxRepository, organisationClient, patientContextClient, eventMapper,
				event -> { }, new CommunicationOutboxProperties(
						"sahha.communication.messages.v1",
						"sahha.communication.referrals.v1", 50,
						java.time.Duration.ofSeconds(5)), clock, null);
	}

	@Transactional
	public ConversationResponse create(UUID organisationId, UUID actorUserId,
			String accessToken, CreateConversationRequest request) {
		if (actorUserId.equals(request.recipientUserId())) {
			throw new IllegalArgumentException("recipient must be another doctor");
		}
		CollaborationDoctorResource actor = organisationClient.resolve(
				organisationId, actorUserId, accessToken);
		CollaborationDoctorResource recipient = organisationClient.resolve(
				organisationId, request.recipientUserId(), accessToken);
		if (request.sourceConsultationId() != null && request.patientRegistrationId() == null) {
			throw new IllegalArgumentException("A consultation source requires patient context");
		}
		if (request.patientRegistrationId() != null) {
			patientContextClient.requireMentionable(organisationId,
					request.patientRegistrationId(), actorUserId, accessToken, request.sourceConsultationId());
		}
		ConversationThread existing = threadRepository
				.findByOrganisationIdAndCreatedByUserIdAndCreationRequestId(
						organisationId, actorUserId, request.conversationRequestId())
				.orElse(null);
		if (existing != null) {
			List<ConversationParticipant> existingParticipants = participants(existing);
			boolean sameRecipient = existingParticipants.stream().anyMatch(value ->
					request.recipientUserId().equals(value.getUserId()));
			if (!sameRecipient || !existing.getSubject().equals(normalizeSubject(request.subject()))
					|| !Objects.equals(existing.getPatientRegistrationId(), request.patientRegistrationId())
					|| !Objects.equals(existing.getSourceConsultationId(), request.sourceConsultationId())) {
				throw new ConversationConflictException();
			}
			return response(existing, actorUserId, existingParticipants);
		}

		Instant now = clock.instant();
		ConversationThread thread = ConversationThread.createWithSource(UUID.randomUUID(),
				organisationId, request.conversationRequestId(), request.subject(),
				request.patientRegistrationId(), actorUserId, actor.membershipId(), now, request.sourceConsultationId());
		threadRepository.save(thread);
		List<ConversationParticipant> participants = List.of(
				ConversationParticipant.join(thread.getId(), organisationId,
						actor.userId(), actor.membershipId(), actor.displayName(), now),
				ConversationParticipant.join(thread.getId(), organisationId,
						recipient.userId(), recipient.membershipId(), recipient.displayName(), now));
		participantRepository.saveAll(participants);
		CommunicationAuditEvent audit = auditRepository.save(CommunicationAuditEvent.record(
				organisationId, thread.getId(), null, actorUserId,
				"CONVERSATION_CREATED", thread.getVersion(), now));
		outboxRepository.save(CommunicationOutboxEvent.pending(audit, outboxProperties.topic(),
				"conversation.created.v1", eventMapper.conversationCreated(audit, thread,
						participants.stream().map(ConversationParticipant::getUserId).toList())));
		return response(thread, actorUserId, participants);
	}

	@Transactional(readOnly = true)
	public ConversationPageResponse list(UUID organisationId, UUID actorUserId,
			String accessToken, int page, int size) {
		validatePage(page, size);
		organisationClient.resolve(organisationId, actorUserId, accessToken);
		Page<ConversationThread> result = threadRepository.findParticipantThreads(
				organisationId, actorUserId, PageRequest.of(page, size));
		return new ConversationPageResponse(result.getContent().stream()
				.map(value -> response(value, actorUserId, participants(value))).toList(),
				result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
	}

	@Transactional(readOnly = true)
	public ConversationResponse find(UUID conversationId, UUID organisationId,
			UUID actorUserId, String accessToken) {
		organisationClient.resolve(organisationId, actorUserId, accessToken);
		ConversationThread thread = requireParticipantThread(
				conversationId, organisationId, actorUserId);
		return response(thread, actorUserId, participants(thread));
	}

	@Transactional(readOnly = true)
	public MessagePageResponse messages(UUID conversationId, UUID organisationId,
			UUID actorUserId, String accessToken, int page, int size) {
		validatePage(page, size);
		organisationClient.resolve(organisationId, actorUserId, accessToken);
		requireParticipantThread(conversationId, organisationId, actorUserId);
		Page<ConversationMessage> result = messageRepository
				.findAllByConversationIdAndOrganisationIdOrderBySentAtDescIdDesc(
						conversationId, organisationId, PageRequest.of(page, size));
        var values=result.getContent().stream().map(this::response).toList();
        if(attachments!=null && values.stream().anyMatch(value->!value.attachments().isEmpty()))
            attachments.requireAccess(conversationId,organisationId,actorUserId,accessToken);
		return new MessagePageResponse(values, result.getNumber(), result.getSize(), result.getTotalElements(),
				result.getTotalPages());
	}

	@Transactional
	public MessageResponse send(UUID conversationId, UUID organisationId,
			UUID actorUserId, String accessToken, SendMessageRequest request) {
		CollaborationDoctorResource actor = organisationClient.resolve(
				organisationId, actorUserId, accessToken);
		ConversationThread thread = requireParticipantThread(
				conversationId, organisationId, actorUserId);
		List<ConversationParticipant> activeParticipants = participants(thread);
		activeParticipants.stream()
				.filter(value -> !value.getUserId().equals(actorUserId))
				.forEach(value -> organisationClient.resolve(
						organisationId, value.getUserId(), accessToken));
		ConversationMessage existing = messageRepository
				.findByConversationIdAndSenderUserIdAndMessageRequestId(
						conversationId, actorUserId, request.messageRequestId()).orElse(null);
		if (existing != null) {
            if(attachments!=null && !response(existing).attachments().isEmpty())
                attachments.requireAccess(conversationId,organisationId,actorUserId,accessToken);
			if (!existing.getBody().equals(request.body().strip())
                    || !response(existing).attachments().stream().map(value->value.fileId()).sorted().toList()
                        .equals(request.attachmentIds().stream().sorted().toList())) {
				throw new ConversationConflictException();
			}
			return response(existing);
		}
        var selected=attachments==null ? java.util.List.<com.sahha.communication.attachment.MessageAttachmentResponse>of()
                :attachments.validate(request.attachmentIds(),organisationId,conversationId,request.messageRequestId(),actorUserId,accessToken);
        if(attachments==null && !request.attachmentIds().isEmpty()) throw new IllegalStateException("Attachment service is unavailable");
		Instant now = clock.instant();
		ConversationMessage message = messageRepository.save(ConversationMessage.send(
				conversationId, organisationId, request.messageRequestId(), actorUserId,
				actor.membershipId(), actor.displayName(), request.body(), now));
        if(!selected.isEmpty()) { messageRepository.flush(); attachments.link(message,selected); }
		thread.messageSent(now);
		CommunicationAuditEvent audit = auditRepository.save(CommunicationAuditEvent.record(
				organisationId, conversationId, message.getId(), actorUserId,
				"MESSAGE_SENT", thread.getVersion() + 1, now));
		List<UUID> recipients = activeParticipants.stream()
				.map(ConversationParticipant::getUserId)
				.filter(value -> !value.equals(actorUserId)).toList();
		outboxRepository.save(CommunicationOutboxEvent.pending(audit, outboxProperties.topic(),
				"message.sent.v1", eventMapper.messageSent(audit, thread, message, recipients)));
		eventPublisher.publishEvent(new CommunicationMessageCreatedEvent(
				message.getId(), conversationId, organisationId, actorUserId,
				message.getSenderDisplayNameSnapshot(), message.getBody(), message.getSentAt(), recipients));
		return response(message);
	}

	@Transactional
	public ConversationResponse markRead(UUID conversationId, UUID organisationId,
			UUID actorUserId, String accessToken) {
		organisationClient.resolve(organisationId, actorUserId, accessToken);
		ConversationThread thread = requireParticipantThread(conversationId, organisationId, actorUserId);
		ConversationParticipant participant = participantRepository
				.findByConversationIdAndOrganisationIdAndUserIdAndActiveTrue(
						conversationId, organisationId, actorUserId)
				.orElseThrow(ConversationNotFoundException::new);
		Instant now = clock.instant();
		if (participant.markRead(now)) {
			auditRepository.save(CommunicationAuditEvent.record(organisationId,
					conversationId, null, actorUserId, "CONVERSATION_READ",
					participant.getVersion() + 1, now));
		}
		return response(thread, actorUserId, participants(thread));
	}

	private ConversationThread requireParticipantThread(UUID id, UUID organisationId, UUID userId) {
		return threadRepository.findParticipantThread(id, organisationId, userId)
				.orElseThrow(ConversationNotFoundException::new);
	}
	private List<ConversationParticipant> participants(ConversationThread thread) {
		return participantRepository
				.findAllByConversationIdAndOrganisationIdAndActiveTrueOrderByJoinedAt(
						thread.getId(), thread.getOrganisationId());
	}
	private ConversationResponse response(ConversationThread thread, UUID actorUserId,
			List<ConversationParticipant> participants) {
		ConversationParticipant actor = participants.stream()
				.filter(value -> actorUserId.equals(value.getUserId())).findFirst()
				.orElseThrow(ConversationNotFoundException::new);
		long unread = messageRepository
				.countByConversationIdAndOrganisationIdAndSenderUserIdNotAndSentAtAfter(
						thread.getId(), thread.getOrganisationId(), actorUserId, actor.getLastReadAt());
		return new ConversationResponse(thread.getId(), thread.getOrganisationId(),
				thread.getSubject(), thread.getPatientRegistrationId(), false,
				thread.getCreatedByUserId(), thread.getCreatedAt(), thread.getLastMessageAt(),
				thread.getVersion(), unread, participants.stream().map(value ->
						new ConversationParticipantResponse(value.getUserId(), value.getMembershipId(),
								value.getDisplayNameSnapshot(), value.getJoinedAt(), value.getLastReadAt()))
						.toList());
	}
	private MessageResponse response(ConversationMessage value) {
		return new MessageResponse(value.getId(), value.getConversationId(),
				value.getSenderUserId(), value.getSenderDisplayNameSnapshot(),
				value.getBody(), value.getSentAt(), attachments==null ? java.util.List.of()
                    :attachments.list(value.getId(),value.getOrganisationId()));
	}
	private static void validatePage(int page, int size) {
		if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid page");
	}
	private static String normalizeSubject(String value) {
		return value.strip().replaceAll("\\s+", " ");
	}
}
