package com.sahha.communication.service.conversationservice;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.sahha.communication.event.CommunicationMessageCreatedEvent;

@Component
public class CommunicationRealtimeDeliveryListener {
	private final CommunicationRealtimeDeliveryService deliveryService;
	public CommunicationRealtimeDeliveryListener(CommunicationRealtimeDeliveryService deliveryService) {
		this.deliveryService = deliveryService;
	}
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void afterCommit(CommunicationMessageCreatedEvent event) {
		deliveryService.deliver(event);
	}
}
