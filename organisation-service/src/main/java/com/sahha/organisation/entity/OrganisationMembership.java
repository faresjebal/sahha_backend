package com.sahha.organisation.entity;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "organisation_membership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OrganisationMembership {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID organisationId;

	@Column(name = "user_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID userId;

	@Column(name = "email_snapshot", nullable = false, length = 254)
	private String emailSnapshot;

	@Column(name = "display_name_snapshot", nullable = false, length = 201)
	private String displayNameSnapshot;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private OrganisationMembershipStatus status;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "updated_by", nullable = false)
	private UUID updatedBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static OrganisationMembership activate(
			UUID organisationId,
			UUID userId,
			String email,
			String firstName,
			String lastName,
			UUID actorUserId,
			Instant joinedAt) {
		OrganisationMembership membership = new OrganisationMembership();
		membership.id = UUID.randomUUID();
		membership.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		membership.userId = Objects.requireNonNull(
				userId,
				"userId must not be null");
		membership.emailSnapshot = required(email, 254, "email")
				.toLowerCase(Locale.ROOT);
		membership.displayNameSnapshot = required(
				required(firstName, 100, "firstName") + " "
						+ required(lastName, 100, "lastName"),
				201,
				"displayName");
		membership.status = OrganisationMembershipStatus.ACTIVE;
		membership.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		membership.updatedBy = actorUserId;
		membership.joinedAt = Objects.requireNonNull(
				joinedAt,
				"joinedAt must not be null");
		membership.createdAt = joinedAt;
		membership.updatedAt = joinedAt;
		return membership;
	}

	public boolean changeStatus(
			OrganisationMembershipStatus requestedStatus,
			UUID actorUserId,
			Instant updatedAt) {
		OrganisationMembershipStatus requiredStatus = Objects.requireNonNull(
				requestedStatus,
				"requestedStatus must not be null");
		if (status == OrganisationMembershipStatus.REMOVED
				&& requiredStatus != OrganisationMembershipStatus.REMOVED) {
			throw new IllegalStateException(
					"a removed membership cannot be reactivated");
		}
		if (status == requiredStatus) {
			return false;
		}
		status = requiredStatus;
		this.updatedBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		Instant requiredUpdatedAt = Objects.requireNonNull(
				updatedAt,
				"updatedAt must not be null");
		if (requiredUpdatedAt.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					"updatedAt must not precede createdAt");
		}
		this.updatedAt = requiredUpdatedAt;
		return true;
	}

	private static String required(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String normalized = value.strip().replaceAll("\\s+", " ");
		if (normalized.isEmpty() || normalized.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return normalized;
	}
}
