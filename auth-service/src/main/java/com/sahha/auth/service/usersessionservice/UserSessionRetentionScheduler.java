package com.sahha.auth.service.usersessionservice;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
		prefix = "sahha.auth.session-retention",
		name = "cleanup-enabled",
		havingValue = "true",
		matchIfMissing = true)
public class UserSessionRetentionScheduler {

	private static final Logger LOGGER = LoggerFactory.getLogger(
			UserSessionRetentionScheduler.class);

	private final UserSessionRetentionService retentionService;
	private final Clock clock;

	public UserSessionRetentionScheduler(
			UserSessionRetentionService retentionService,
			Clock clock) {
		this.retentionService = retentionService;
		this.clock = clock;
	}

	@Scheduled(
			fixedDelayString =
					"${sahha.auth.session-retention.fixed-delay:PT1H}")
	public void purgeExpiredRefreshTokenFamilies() {
		int deleted = retentionService.purgeExpiredRefreshTokenFamilies(
				clock.instant());
		if (deleted > 0) {
			LOGGER.info(
					"Purged {} expired refresh-token lineage rows",
					deleted);
		}
	}
}
