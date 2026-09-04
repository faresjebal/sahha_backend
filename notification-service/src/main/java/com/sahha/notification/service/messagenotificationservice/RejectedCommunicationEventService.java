package com.sahha.notification.service.messagenotificationservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.entity.RejectedCommunicationEvent;
import com.sahha.notification.event.CommunicationEventSource;
import com.sahha.notification.repository.RejectedCommunicationEventRepository;

@Service
public class RejectedCommunicationEventService {
	private static final byte[] NULL_PAYLOAD = "<null>".getBytes(StandardCharsets.UTF_8);
	private final RejectedCommunicationEventRepository repository;
	private final Clock clock;

	public RejectedCommunicationEventService(
			RejectedCommunicationEventRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public boolean record(CommunicationEventSource source, String payload,
			String reasonCode) {
		if (repository.existsBySourceTopicAndSourcePartitionAndSourceOffset(
				source.topic(), source.partition(), source.offset())) return false;
		repository.save(RejectedCommunicationEvent.record(
				source, sha256(payload), reasonCode, clock.instant()));
		return true;
	}

	private static String sha256(String payload) {
		try {
			byte[] value = payload == null ? NULL_PAYLOAD
					: payload.getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(value));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
