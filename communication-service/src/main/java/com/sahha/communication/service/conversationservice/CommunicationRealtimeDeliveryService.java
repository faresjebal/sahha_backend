package com.sahha.communication.service.conversationservice;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.sahha.communication.config.CommunicationWebSocketConfiguration;
import com.sahha.communication.dto.response.RealtimeMessageResponse;
import com.sahha.communication.event.CommunicationMessageCreatedEvent;
import com.sahha.communication.security.CommunicationPrincipalName;

@Service
public class CommunicationRealtimeDeliveryService {
	private final SimpMessagingTemplate messagingTemplate;
	public CommunicationRealtimeDeliveryService(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}
	public void deliver(CommunicationMessageCreatedEvent event) {
		for (var recipient : event.recipientUserIds()) {
			messagingTemplate.convertAndSendToUser(
					CommunicationPrincipalName.of(recipient, event.organisationId()),
					CommunicationWebSocketConfiguration.USER_DESTINATION.substring("/user".length()),
					RealtimeMessageResponse.created(event.messageId(), event.conversationId(),
							event.senderUserId(), event.senderDisplayName(), event.body(), event.sentAt()));
		}
	}
}
