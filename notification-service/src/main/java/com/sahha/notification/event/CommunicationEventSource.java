package com.sahha.notification.event;

public record CommunicationEventSource(String topic, int partition, long offset) {
	public CommunicationEventSource {
		if (topic == null || topic.isBlank() || partition < 0 || offset < 0) {
			throw new IllegalArgumentException("communication event source is invalid");
		}
	}
}
