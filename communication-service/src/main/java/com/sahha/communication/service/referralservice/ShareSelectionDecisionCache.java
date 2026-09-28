package com.sahha.communication.service.referralservice;

import java.time.Clock;
import java.time.Duration;
import java.util.function.BooleanSupplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.sahha.communication.dto.request.ShareAccessDecisionRequest;
import com.sahha.communication.entity.ReferralSharingGrant;

/** Caches immutable item selection only. Callers MUST first read an active grant
 * from PostgreSQL and validate current memberships. A cache hit is never a grant. */
@Component
public class ShareSelectionDecisionCache {
	private static final Duration MAX_TTL = Duration.ofSeconds(5);
	private final StringRedisTemplate redis;
	private final Clock clock;
	private final boolean enabled;

	public ShareSelectionDecisionCache(StringRedisTemplate redis, Clock clock,
			@Value("${sahha.communication.sharing.cache-enabled:true}") boolean enabled) {
		this.redis = redis;
		this.clock = clock;
		this.enabled = enabled;
	}

	public boolean selected(ReferralSharingGrant grant, ShareAccessDecisionRequest request,
			BooleanSupplier authoritativeLookup) {
		if (!clock.instant().isBefore(grant.getValidUntil())) return false;
		String key = key(grant);
		String field = grant.getVersion() + ":" + request.resourceType() + ":" + request.resourceId();
		if (enabled) {
			try {
				Object cached = redis.opsForHash().get(key, field);
				if ("1".equals(cached)) return true;
				if ("0".equals(cached)) return false;
			}
			catch (RuntimeException unavailable) { /* PostgreSQL remains authoritative. */ }
		}
		boolean selected = authoritativeLookup.getAsBoolean();
		if (enabled) {
			try {
				Duration ttl = Duration.between(clock.instant(), grant.getValidUntil());
				if (ttl.compareTo(MAX_TTL) > 0) ttl = MAX_TTL;
				if (ttl.toMillis() > 0) {
					// One atomic operation ensures a failed expiry cannot leave permanent keys.
					redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>(
							"redis.call('HSET',KEYS[1],ARGV[1],ARGV[2]); "
							+ "redis.call('PEXPIRE',KEYS[1],ARGV[3]); return 1", Long.class),
							java.util.List.of(key), field, selected ? "1" : "0",
							Long.toString(ttl.toMillis()));
				}
			}
			catch (RuntimeException unavailable) { /* No cached authorization fallback. */ }
		}
		return selected;
	}

	public void invalidateAfterCommit(ReferralSharingGrant grant) {
		if (!enabled) return;
		String key = key(grant);
		Runnable invalidate = () -> {
			try { redis.delete(key); }
			catch (RuntimeException unavailable) {
				// Even after failed eviction or concurrent refill, the database status/version
				// gate prevents use of terminated grants. Remaining entries expire in 5 seconds.
			}
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override public void afterCommit() { invalidate.run(); }
			});
		}
		else invalidate.run();
	}

	private static String key(ReferralSharingGrant grant) {
		return "sahha:communication:share-selection:v1:" + grant.getOrganisationId()
				+ ":" + grant.getRecipientMembershipId() + ":" + grant.getId();
	}
}
