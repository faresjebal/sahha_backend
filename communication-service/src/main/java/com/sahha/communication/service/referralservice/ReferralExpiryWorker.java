package com.sahha.communication.service.referralservice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "sahha.communication.referrals",
		name = "expiry-enabled", havingValue = "true")
public class ReferralExpiryWorker {
	private final ReferralService service;

	public ReferralExpiryWorker(ReferralService service) { this.service = service; }

	@Scheduled(fixedDelayString = "${sahha.communication.referrals.expiry-delay:PT1M}")
	public int expireDue() { return service.expireDue(); }
}
