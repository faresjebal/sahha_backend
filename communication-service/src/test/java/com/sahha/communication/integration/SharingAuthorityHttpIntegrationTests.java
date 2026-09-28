package com.sahha.communication.integration;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.sahha.communication.client.organisation.*;
import com.sahha.communication.dto.request.*;
import com.sahha.communication.entity.*;
import com.sahha.communication.exception.ConversationNotFoundException;
import com.sahha.communication.repository.*;
import com.sahha.communication.service.referralservice.ReferralService;

@SpringBootTest(properties = "sahha.communication.sharing.cache-enabled=true")
@AutoConfigureMockMvc
@Transactional
class SharingAuthorityHttpIntegrationTests {
    private static final String TOKEN = "sharing.recipient.token";
    private static final Instant NOW = Instant.parse("2026-09-07T12:00:00Z");
    private final UUID organisation = UUID.randomUUID(), patient = UUID.randomUUID(),
            owner = UUID.randomUUID(), ownerMembership = UUID.randomUUID(),
            recipient = UUID.randomUUID(), recipientMembership = UUID.randomUUID(), resource = UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired ReferralService referrals;
    @Autowired ReferralRequestRepository requests;
    @Autowired ReferralShareItemRepository items;
    @MockitoBean OrganisationCollaborationClient directory;
    @MockitoBean JwtDecoder decoder;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean Clock clock;
    private HashOperations<String, Object, Object> cached;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        when(clock.instant()).thenReturn(NOW);
        cached = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(cached);
        // Deliberately retain a stale positive cache value; the DB must still stop access.
        when(cached.get(anyString(), anyString())).thenReturn("1");
        when(directory.resolve(eq(organisation), any(UUID.class), eq(TOKEN))).thenAnswer(call -> {
            UUID user = call.getArgument(1);
            return new CollaborationDoctorResource(user.equals(owner) ? ownerMembership : recipientMembership,
                    organisation, user, "Synthetic doctor", 1);
        });
        when(decoder.decode(TOKEN)).thenReturn(Jwt.withTokenValue(TOKEN).header("alg", "RS256")
                .subject(recipient.toString()).issuer("http://localhost:8081").audience(List.of("sahha-api"))
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(300)).claim("sid", UUID.randomUUID().toString())
                .claim("cv", 1).claim("roles", List.of()).claim("org_id", organisation.toString())
                .claim("org_roles", List.of("DOCTOR")).claim("token_type", "access").build());
    }

    @Test void acceptanceThenRevocationInvalidatesAccessEvenWithStaleRedis() throws Exception {
        var referral = sent(); decision(false);
        var active = referrals.accept(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(0));
        decision(true);
        referrals.revoke(referral.getId(), organisation, owner, TOKEN,
                new ReferralReasonedCommandRequest(active.version(), "Synthetic withdrawal"));
        decision(false);
        verify(cached, times(1)).get(anyString(), anyString());
    }

    @Test void expiryBeforeWorkerStopsCachedAccess() throws Exception {
        var referral = sent();
        referrals.accept(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(0));
        decision(true);
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));
        decision(false);
        // The scheduled worker also persists the terminal state and its audit/outbox.
        org.junit.jupiter.api.Assertions.assertEquals(1, referrals.expireDue());
        decision(false);
    }

    @Test void completedReferralCannotUseAnEarlierCachedDecision() throws Exception {
        var referral = sent();
        var active = referrals.accept(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(0));
        decision(true);
        referrals.complete(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(active.version()));
        decision(false);
    }

    @Test void patientOwnerAndMembershipMustMatchEvenWhenAnItemIdIsSelected() throws Exception {
        var referral = sent();
        referrals.accept(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(0));
        mvc.perform(query().param("resourceOwnerMembershipId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(get("/api/v1/sharing/access-decisions").cookie(access())
                        .param("patientRegistrationId", patient.toString()).param("resourceType", "CONSULTATION")
                        .param("resourceId", resource.toString()).param("resourceOwnerUserId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        mvc.perform(get("/api/v1/sharing/access-decisions").cookie(access())
                        .param("patientRegistrationId", UUID.randomUUID().toString()).param("resourceType", "CONSULTATION")
                        .param("resourceId", resource.toString()).param("resourceOwnerUserId", owner.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
        doThrow(new ConversationNotFoundException()).when(directory).resolve(organisation, owner, TOKEN);
        mvc.perform(query()).andExpect(status().isNotFound());
        verifyNoInteractions(cached);
    }

    @Test void redisFailureFallsBackToTheSelectedItemDatabaseCheck() throws Exception {
        var referral = sent();
        referrals.accept(referral.getId(), organisation, recipient, TOKEN, new ReferralVersionRequest(0));
        when(cached.get(anyString(), anyString())).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("synthetic outage"));
        decision(true);
        mvc.perform(get("/api/v1/sharing/access-decisions").cookie(access())
                        .param("patientRegistrationId", patient.toString()).param("resourceType", "MEDICATION")
                        .param("resourceId", resource.toString()).param("resourceOwnerUserId", owner.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.allowed").value(false));
    }

    private ReferralRequest sent() {
        var referral = requests.saveAndFlush(ReferralRequest.create(organisation, UUID.randomUUID(), patient,
                owner, ownerMembership, "Synthetic owner", recipient, recipientMembership, "Synthetic recipient",
                "Synthetic review", ReferralPriority.ROUTINE, null, "Synthetic specialist opinion",
                ConsentType.RECORDED_VERBAL, "synthetic-consent", NOW.minusSeconds(1), NOW.plusSeconds(60), true, NOW));
        items.saveAndFlush(ReferralShareItem.select(referral.getId(), organisation, ShareResourceType.CONSULTATION, resource, NOW));
        return referral;
    }
    private void decision(boolean allowed) throws Exception {
        mvc.perform(query()).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.allowed").value(allowed));
    }
    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder query() {
        return get("/api/v1/sharing/access-decisions").cookie(access())
                .param("patientRegistrationId", patient.toString()).param("resourceType", "CONSULTATION")
                .param("resourceId", resource.toString()).param("resourceOwnerUserId", owner.toString());
    }
    private Cookie access() { return new Cookie("SAHHA_ACCESS_TOKEN", TOKEN); }
}
