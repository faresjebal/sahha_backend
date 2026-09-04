package com.sahha.notification.service.appointmentnotificationservice;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.notification.entity.RejectedAppointmentEvent;
import com.sahha.notification.event.AppointmentEventSource;
import com.sahha.notification.repository.RejectedAppointmentEventRepository;

@Service
public class RejectedAppointmentEventService {

	private static final byte[] NULL_PAYLOAD =
			"<null>".getBytes(StandardCharsets.UTF_8);

	private final RejectedAppointmentEventRepository repository;
	private final Clock clock;

	public RejectedAppointmentEventService(
			RejectedAppointmentEventRepository repository,
			Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public boolean record(
			AppointmentEventSource source,
			String payload,
			String reasonCode) {
		if (repository.existsBySourceTopicAndSourcePartitionAndSourceOffset(
				source.topic(), source.partition(), source.offset())) {
			return false;
		}
		repository.save(RejectedAppointmentEvent.record(
				source,
				sha256(payload),
				reasonCode,
				clock.instant()));
		return true;
	}

	private static String sha256(String payload) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] value = payload == null
					? NULL_PAYLOAD
					: payload.getBytes(StandardCharsets.UTF_8);
			return HexFormat.of().formatHex(digest.digest(value));
		}
		catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}
}
