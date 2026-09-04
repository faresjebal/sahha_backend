package com.sahha.notification.event;

public record AppointmentEventSource(
		String topic,
		int partition,
		long offset) {

	public AppointmentEventSource {
		if (topic == null || topic.isBlank() || topic.length() > 249) {
			throw new IllegalArgumentException("source topic is invalid");
		}
		if (partition < 0 || offset < 0) {
			throw new IllegalArgumentException("source position is invalid");
		}
	}
}
