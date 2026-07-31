package com.sahha.auth.service.usersessionservice;

import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sahha.auth.config.AuthSessionRetentionProperties;
import com.sahha.auth.repository.RefreshTokenRepository;

@Service
public class UserSessionRetentionService {

	private final RefreshTokenRepository refreshTokenRepository;
	private final AuthSessionRetentionProperties properties;

	public UserSessionRetentionService(
			RefreshTokenRepository refreshTokenRepository,
			AuthSessionRetentionProperties properties) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.properties = properties;
	}

	@Transactional
	public int purgeExpiredRefreshTokenFamilies(Instant observedAt) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		Instant purgeBefore = requiredObservedAt.minus(
				properties.refreshTokenRetention());
		return refreshTokenRepository.deleteExpiredSessionTokenFamilies(
				purgeBefore,
				properties.familyBatchSize());
	}
}
