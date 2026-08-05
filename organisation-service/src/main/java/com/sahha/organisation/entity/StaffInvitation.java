package com.sahha.organisation.entity;

import java.time.Duration;
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
@Table(name = "staff_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class StaffInvitation {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "organisation_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID organisationId;

	@Column(nullable = false, length = 254, updatable = false)
	private String email;

	@Column(name = "normalized_email", nullable = false, length = 254,
			updatable = false)
	private String normalizedEmail;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	@ToString.Include
	private OrganisationRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	@ToString.Include
	private StaffInvitationStatus status;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolved_by_user_id")
	private UUID resolvedByUserId;

	@Column(name = "accepted_membership_id")
	private UUID acceptedMembershipId;

	@Column(name = "created_by", nullable = false, updatable = false)
	private UUID createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static StaffInvitation create(
			UUID organisationId,
			String email,
			OrganisationRole role,
			UUID actorUserId,
			Instant createdAt,
			Duration validity) {
		StaffInvitation invitation = new StaffInvitation();
		invitation.id = UUID.randomUUID();
		invitation.organisationId = Objects.requireNonNull(
				organisationId,
				"organisationId must not be null");
		invitation.email = required(email, 254, "email");
		invitation.normalizedEmail = normalizeEmail(invitation.email);
		invitation.role = requireInvitableRole(role);
		invitation.status = StaffInvitationStatus.PENDING;
		invitation.createdBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		invitation.createdAt = Objects.requireNonNull(
				createdAt,
				"createdAt must not be null");
		invitation.updatedAt = createdAt;
		invitation.expiresAt = createdAt.plus(requireValidity(validity));
		return invitation;
	}

	public StaffInvitationStatus statusAt(Instant observedAt) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		return status == StaffInvitationStatus.PENDING
				&& !requiredObservedAt.isBefore(expiresAt)
					? StaffInvitationStatus.EXPIRED
					: status;
	}

	public void renew(Instant renewedAt, Duration validity) {
		requirePending();
		Instant requiredRenewedAt = requireNotBeforeCreation(
				renewedAt,
				"renewedAt");
		expiresAt = requiredRenewedAt.plus(requireValidity(validity));
		updatedAt = requiredRenewedAt;
	}

	public void revoke(UUID actorUserId, Instant revokedAt) {
		requireUsable(revokedAt);
		resolve(StaffInvitationStatus.REVOKED, actorUserId, revokedAt);
	}

	public void reject(UUID actorUserId, Instant rejectedAt) {
		requireUsable(rejectedAt);
		resolve(StaffInvitationStatus.REJECTED, actorUserId, rejectedAt);
	}

	public void accept(
			UUID actorUserId,
			UUID membershipId,
			Instant acceptedAt) {
		requireUsable(acceptedAt);
		resolve(StaffInvitationStatus.ACCEPTED, actorUserId, acceptedAt);
		acceptedMembershipId = Objects.requireNonNull(
				membershipId,
				"membershipId must not be null");
	}

	public boolean targets(String candidateEmail) {
		return normalizedEmail.equals(normalizeEmail(candidateEmail));
	}

	public static String normalizeEmail(String value) {
		return required(value, 254, "email").toLowerCase(Locale.ROOT);
	}

	private void resolve(
			StaffInvitationStatus resolvedStatus,
			UUID actorUserId,
			Instant resolvedAt) {
		this.status = resolvedStatus;
		this.resolvedByUserId = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		this.resolvedAt = requireNotBeforeCreation(resolvedAt, "resolvedAt");
		this.updatedAt = this.resolvedAt;
	}

	private void requirePending() {
		if (status != StaffInvitationStatus.PENDING) {
			throw new IllegalStateException("staff invitation is already resolved");
		}
	}

	private void requireUsable(Instant observedAt) {
		requirePending();
		if (statusAt(observedAt) == StaffInvitationStatus.EXPIRED) {
			throw new IllegalStateException("staff invitation has expired");
		}
	}

	private Instant requireNotBeforeCreation(
			Instant value,
			String fieldName) {
		Instant required = Objects.requireNonNull(
				value,
				fieldName + " must not be null");
		if (required.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					fieldName + " must not precede creation");
		}
		return required;
	}

	private static OrganisationRole requireInvitableRole(
			OrganisationRole role) {
		OrganisationRole requiredRole = Objects.requireNonNull(
				role,
				"role must not be null");
		if (requiredRole != OrganisationRole.DOCTOR
				&& requiredRole != OrganisationRole.RECEPTIONIST) {
			throw new IllegalArgumentException("role is not invitable");
		}
		return requiredRole;
	}

	private static Duration requireValidity(Duration validity) {
		Duration requiredValidity = Objects.requireNonNull(
				validity,
				"validity must not be null");
		if (requiredValidity.isNegative() || requiredValidity.isZero()) {
			throw new IllegalArgumentException("validity must be positive");
		}
		return requiredValidity;
	}

	private static String required(
			String value,
			int maximumLength,
			String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String stripped = value.strip();
		if (stripped.isEmpty() || stripped.length() > maximumLength) {
			throw new IllegalArgumentException(fieldName + " is invalid");
		}
		return stripped;
	}
}
