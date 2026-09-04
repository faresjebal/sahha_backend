package com.sahha.scheduling.event;

public record ClinicalEventSource(
		String type,
		String topic,
		Integer partition,
		Long offset) {

	public static ClinicalEventSource kafka(
			String topic, int partition, long offset) {
		return new ClinicalEventSource("KAFKA", topic, partition, offset);
	}

	public static ClinicalEventSource rest() {
		return new ClinicalEventSource("REST", null, null, null);
	}
}
