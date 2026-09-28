package com.sahha.communication.service.conversationservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sahha.communication.client.organisation.CollaborationDoctorResource;
import com.sahha.communication.client.organisation.OrganisationCollaborationClient;
import com.sahha.communication.client.scheduling.SchedulingPatientContextClient;
import com.sahha.communication.dto.request.CreateConversationRequest;
import com.sahha.communication.dto.request.SendMessageRequest;
import com.sahha.communication.entity.CommunicationAuditEvent;
import com.sahha.communication.entity.CommunicationOutboxEvent;
import com.sahha.communication.entity.ConversationMessage;
import com.sahha.communication.entity.ConversationParticipant;
import com.sahha.communication.entity.ConversationThread;
import com.sahha.communication.event.CommunicationEventMapper;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.CommunicationAuditEventRepository;
import com.sahha.communication.repository.CommunicationOutboxEventRepository;
import com.sahha.communication.repository.ConversationMessageRepository;
import com.sahha.communication.repository.ConversationParticipantRepository;
import com.sahha.communication.repository.ConversationThreadRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTests {
	private static final UUID ORGANISATION_ID = UUID.randomUUID();
	private static final UUID ACTOR_ID = UUID.randomUUID();
	private static final UUID RECIPIENT_ID = UUID.randomUUID();
	private static final UUID ACTOR_MEMBERSHIP_ID = UUID.randomUUID();
	private static final UUID RECIPIENT_MEMBERSHIP_ID = UUID.randomUUID();
	private static final UUID PATIENT_REGISTRATION_ID = UUID.randomUUID();
	private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");
	private static final String TOKEN = "synthetic.jwt.token";

	@Mock ConversationThreadRepository threadRepository;
	@Mock ConversationParticipantRepository participantRepository;
	@Mock ConversationMessageRepository messageRepository;
	@Mock CommunicationAuditEventRepository auditRepository;
	@Mock CommunicationOutboxEventRepository outboxRepository;
	@Mock OrganisationCollaborationClient organisationClient;
	@Mock SchedulingPatientContextClient patientContextClient;
	@Mock com.sahha.communication.client.clinical.ClinicalCollaborationSourceClient clinicalSourceClient;
	private ConversationService service;

	@BeforeEach
	void setUp() {
		service = new ConversationService(threadRepository, participantRepository,
				messageRepository, auditRepository, outboxRepository,
				organisationClient, new CollaborationPatientContextService(patientContextClient, clinicalSourceClient), new CommunicationEventMapper(),
				Clock.fixed(NOW, ZoneOffset.UTC));
		lenient().when(auditRepository.save(any())).thenAnswer(call -> call.getArgument(0));
	}

	@Test
	void patientMentionRequiresExistingSenderAccessButGrantsNothing() {
		when(organisationClient.resolve(ORGANISATION_ID, ACTOR_ID, TOKEN))
				.thenReturn(doctor(ACTOR_ID, ACTOR_MEMBERSHIP_ID, "Dr Actor"));
		when(organisationClient.resolve(ORGANISATION_ID, RECIPIENT_ID, TOKEN))
				.thenReturn(doctor(RECIPIENT_ID, RECIPIENT_MEMBERSHIP_ID, "Dr Recipient"));
		when(threadRepository.findByOrganisationIdAndCreatedByUserIdAndCreationRequestId(
				any(), any(), any())).thenReturn(Optional.empty());

		var result = service.create(ORGANISATION_ID, ACTOR_ID, TOKEN,
				new CreateConversationRequest(UUID.randomUUID(), RECIPIENT_ID,
						"Synthetic cardiology discussion", PATIENT_REGISTRATION_ID));

		verify(patientContextClient).requireMentionable(ORGANISATION_ID,
				PATIENT_REGISTRATION_ID, ACTOR_ID, TOKEN);
		assertFalse(result.patientAccessGranted());
		assertEquals(PATIENT_REGISTRATION_ID, result.patientRegistrationId());
		ArgumentCaptor<CommunicationOutboxEvent> captor =
				ArgumentCaptor.forClass(CommunicationOutboxEvent.class);
		verify(outboxRepository).save(captor.capture());
		assertFalse(captor.getValue().getPayload().containsKey("patientRegistrationId"));
		assertFalse(captor.getValue().getPayload().containsKey("body"));
	}

	@Test
	void nonParticipantCannotReadConversation() {
		when(organisationClient.resolve(ORGANISATION_ID, ACTOR_ID, TOKEN))
				.thenReturn(doctor(ACTOR_ID, ACTOR_MEMBERSHIP_ID, "Dr Actor"));
		when(threadRepository.findParticipantThread(any(), any(), any()))
				.thenReturn(Optional.empty());

		assertThrows(ConversationNotFoundException.class, () -> service.find(
				UUID.randomUUID(), ORGANISATION_ID, ACTOR_ID, TOKEN));
	}

	@Test
	void messageEventContainsRoutingMetadataButNotMessageOrPatientData() {
		ConversationThread thread = ConversationThread.create(UUID.randomUUID(),
				ORGANISATION_ID, UUID.randomUUID(), "Synthetic care coordination",
				PATIENT_REGISTRATION_ID, ACTOR_ID, ACTOR_MEMBERSHIP_ID, NOW.minusSeconds(60));
		List<ConversationParticipant> participants = List.of(
				ConversationParticipant.join(thread.getId(), ORGANISATION_ID, ACTOR_ID,
						ACTOR_MEMBERSHIP_ID, "Dr Actor", NOW.minusSeconds(60)),
				ConversationParticipant.join(thread.getId(), ORGANISATION_ID, RECIPIENT_ID,
						RECIPIENT_MEMBERSHIP_ID, "Dr Recipient", NOW.minusSeconds(60)));
		when(organisationClient.resolve(ORGANISATION_ID, ACTOR_ID, TOKEN))
				.thenReturn(doctor(ACTOR_ID, ACTOR_MEMBERSHIP_ID, "Dr Actor"));
		when(organisationClient.resolve(ORGANISATION_ID, RECIPIENT_ID, TOKEN))
				.thenReturn(doctor(RECIPIENT_ID, RECIPIENT_MEMBERSHIP_ID, "Dr Recipient"));
		when(threadRepository.findParticipantThread(thread.getId(), ORGANISATION_ID, ACTOR_ID))
				.thenReturn(Optional.of(thread));
		when(messageRepository.findByConversationIdAndSenderUserIdAndMessageRequestId(
				any(), any(), any())).thenReturn(Optional.empty());
		when(messageRepository.save(any())).thenAnswer(call -> call.getArgument(0));
		when(participantRepository
				.findAllByConversationIdAndOrganisationIdAndActiveTrueOrderByJoinedAt(
						thread.getId(), ORGANISATION_ID)).thenReturn(participants);

		service.send(thread.getId(), ORGANISATION_ID, ACTOR_ID, TOKEN,
				new SendMessageRequest(UUID.randomUUID(), "Synthetic message body"));

		ArgumentCaptor<CommunicationOutboxEvent> captor =
				ArgumentCaptor.forClass(CommunicationOutboxEvent.class);
		verify(outboxRepository).save(captor.capture());
		var payload = captor.getValue().getPayload();
		assertEquals("message.sent.v1", payload.get("eventType"));
		assertFalse(payload.containsKey("body"));
		assertFalse(payload.containsKey("patientRegistrationId"));
	}

	private static CollaborationDoctorResource doctor(
			UUID userId, UUID membershipId, String name) {
		return new CollaborationDoctorResource(membershipId, ORGANISATION_ID,
				userId, name, 1);
	}
}
