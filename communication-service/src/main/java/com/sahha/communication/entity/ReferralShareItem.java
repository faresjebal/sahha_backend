package com.sahha.communication.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "referral_share_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReferralShareItem {
	@Id @Column(nullable = false, updatable = false)
	private UUID id;
	@Column(name = "referral_id", nullable = false, updatable = false)
	private UUID referralId;
	@Column(name = "organisation_id", nullable = false, updatable = false)
	private UUID organisationId;
	@Enumerated(EnumType.STRING)
	@Column(name = "resource_type", nullable = false, length = 32, updatable = false)
	private ShareResourceType resourceType;
	@Column(name = "resource_id", nullable = false, updatable = false)
	private UUID resourceId;
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public static ReferralShareItem select(UUID referralId, UUID organisationId,
			ShareResourceType resourceType, UUID resourceId, Instant now) {
		ReferralShareItem value = new ReferralShareItem();
		value.id = UUID.randomUUID();
		value.referralId = Objects.requireNonNull(referralId);
		value.organisationId = Objects.requireNonNull(organisationId);
		value.resourceType = Objects.requireNonNull(resourceType);
		value.resourceId = Objects.requireNonNull(resourceId);
		value.createdAt = Objects.requireNonNull(now);
		return value;
	}
}
