package com.sahha.communication.service.referralservice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.sahha.communication.dto.request.ShareAccessDecisionRequest;
import com.sahha.communication.entity.*;

class ShareSelectionDecisionCacheTests {
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");

    @Test void nativeRedisCachesExactSelectionsWithBoundedTtlAndInvalidatesAfterCommit() {
        var configuration = new org.springframework.data.redis.connection.RedisStandaloneConfiguration(
                "127.0.0.1", Integer.getInteger("sahha.test.redis.port", 6379));
        String password = System.getenv("COMMUNICATION_TEST_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) configuration.setPassword(password);
        var connection = new LettuceConnectionFactory(configuration);
        connection.afterPropertiesSet(); connection.start();
        var redis = new StringRedisTemplate(connection);
        var grant = grant();
        String key = "sahha:communication:share-selection:v1:" + grant.getOrganisationId()
                + ":" + grant.getRecipientMembershipId() + ":" + grant.getId();
        try {
            var cache = new ShareSelectionDecisionCache(redis, Clock.fixed(NOW, ZoneOffset.UTC), true);
            var request = request(grant, UUID.randomUUID());
            var lookups = new AtomicInteger();
            assertTrue(cache.selected(grant, request, () -> { lookups.incrementAndGet(); return true; }));
            assertTrue(cache.selected(grant, request, () -> { fail("must use cached exact selection"); return false; }));
            assertEquals(1, lookups.get());
            Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
            assertNotNull(ttl); assertTrue(ttl > 0 && ttl <= 5000);
            assertFalse(cache.selected(grant, request(grant, UUID.randomUUID()), () -> false));
            var expiredCache = new ShareSelectionDecisionCache(redis,
                    Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), true);
            assertFalse(expiredCache.selected(grant, request, () -> { fail("expiry must deny"); return true; }));

            TransactionSynchronizationManager.initSynchronization();
            try {
                cache.invalidateAfterCommit(grant);
                assertTrue(redis.hasKey(key));
                TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
                assertFalse(redis.hasKey(key));
            }
            finally { TransactionSynchronizationManager.clearSynchronization(); }
        }
        finally {
            // The sole key belongs to this random test grant; never flush or scan shared Redis data.
            try { redis.delete(key); } finally { connection.destroy(); }
        }
    }

    @Test void redisOutageAndFailedInvalidationDoNotReplaceDatabaseDecisions() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.opsForHash()).thenThrow(new IllegalStateException("synthetic outage"));
        doThrow(new IllegalStateException("synthetic outage")).when(redis).delete(anyString());
        var grant = grant();
        var cache = new ShareSelectionDecisionCache(redis, Clock.fixed(NOW, ZoneOffset.UTC), true);
        assertFalse(cache.selected(grant, request(grant, UUID.randomUUID()), () -> false));
        assertTrue(cache.selected(grant, request(grant, UUID.randomUUID()), () -> true));
        assertDoesNotThrow(() -> cache.invalidateAfterCommit(grant));
    }

    private static ReferralSharingGrant grant() {
        UUID owner = UUID.randomUUID(), recipient = UUID.randomUUID();
        var referral = ReferralRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                owner, UUID.randomUUID(), "Synthetic owner", recipient, UUID.randomUUID(), "Synthetic recipient",
                "Synthetic review", ReferralPriority.ROUTINE, null, "Synthetic purpose",
                ConsentType.RECORDED_VERBAL, "synthetic-consent", NOW.minusSeconds(1), NOW.plusSeconds(60), true, NOW);
        referral.accept(recipient, 0, NOW);
        return ReferralSharingGrant.activate(referral, NOW);
    }
    private static ShareAccessDecisionRequest request(ReferralSharingGrant grant, UUID resource) {
        return new ShareAccessDecisionRequest(grant.getPatientRegistrationId(), ShareResourceType.CONSULTATION,
                resource, UUID.randomUUID(), null);
    }
}
