package com.sahha.communication.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.sahha.communication.exception.ReferralStateConflictException;
import com.sahha.communication.exception.ReferralVersionConflictException;

class ReferralRequestTests {
	private static final Instant NOW = Instant.parse("2026-09-04T12:00:00Z");
	private static final UUID SENDER_ID = UUID.randomUUID();
	private static final UUID RECIPIENT_ID = UUID.randomUUID();

	@Test
	void draftCanBeSentAcceptedAndCompletedWithASelectedGrant() {
		ReferralRequest referral = referral(false);
		assertEquals(ReferralStatus.DRAFT, referral.getStatus());
		assertEquals(ReferralType.SECOND_OPINION, referral.getReferralType());
		referral.send(SENDER_ID, 0, NOW.plusSeconds(1));
		assertEquals(ReferralStatus.SENT, referral.getStatus());
		referral.accept(RECIPIENT_ID, 0, NOW.plusSeconds(2));
		assertEquals(ReferralStatus.ACTIVE, referral.getStatus());
		assertTrue(referral.getAcceptedAt().isBefore(referral.getAccessExpiresAt()));
		ReferralSharingGrant grant = ReferralSharingGrant.activate(referral, NOW.plusSeconds(2));
		assertThrows(IllegalArgumentException.class, () -> ReferralCareParticipation.begin(referral, grant));
		assertEquals(SharingGrantStatus.ACTIVE, grant.getStatus());
		referral.complete(RECIPIENT_ID, 0, NOW.plusSeconds(3));
		grant.revoke(NOW.plusSeconds(3), "REFERRAL_COMPLETED");
		assertEquals(ReferralStatus.COMPLETED, referral.getStatus());
		assertEquals(SharingGrantStatus.REVOKED, grant.getStatus());
	}

	@Test
	void sentReferralCanBeRejectedButNotAcceptedAfterwards() {
		ReferralRequest referral = referral(true);
		referral.reject(RECIPIENT_ID, 0, "Synthetic recipient declined", NOW.plusSeconds(1));
		assertEquals(ReferralStatus.REJECTED, referral.getStatus());
		assertThrows(ReferralStateConflictException.class,
				() -> referral.accept(RECIPIENT_ID, 0, NOW.plusSeconds(2)));
	}

	@Test
	void activeReferralExpiresAndStaleCommandsAreRejected() {
		ReferralRequest referral = referral(true);
		assertThrows(ReferralVersionConflictException.class,
				() -> referral.accept(RECIPIENT_ID, 1, NOW.plusSeconds(1)));
		referral.accept(RECIPIENT_ID, 0, NOW.plusSeconds(1));
		UUID systemActorId = new UUID(0, 0);
		assertFalse(referral.expire(systemActorId, NOW.plus(6, ChronoUnit.DAYS)));
		assertTrue(referral.expire(systemActorId, NOW.plus(8, ChronoUnit.DAYS)));
		assertEquals(ReferralStatus.EXPIRED, referral.getStatus());
	}

	private static ReferralRequest referral(boolean sendImmediately) {
		return ReferralRequest.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				SENDER_ID, UUID.randomUUID(), "Dr Synthetic Sender", RECIPIENT_ID,
				UUID.randomUUID(), "Dr Synthetic Recipient", "Synthetic specialist review",
				ReferralPriority.ROUTINE, "Synthetic summary", "Synthetic specialist opinion",
				ConsentType.RECORDED_VERBAL, "synthetic-consent", NOW.minusSeconds(30),
				NOW.plus(7, ChronoUnit.DAYS), sendImmediately, NOW);
	}
}
