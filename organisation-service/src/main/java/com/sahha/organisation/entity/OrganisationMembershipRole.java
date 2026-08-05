package com.sahha.organisation.entity;

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
import lombok.ToString;

@Entity
@Table(name = "organisation_membership_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class OrganisationMembershipRole {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "membership_id", nullable = false, updatable = false)
	private UUID membershipId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	@ToString.Include
	private OrganisationRole role;

	@Column(name = "assigned_by", nullable = false, updatable = false)
	private UUID assignedBy;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	@Column(nullable = false)
	private boolean active;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	public static OrganisationMembershipRole assign(
			OrganisationMembership membership,
			OrganisationRole role,
			UUID actorUserId,
			Instant assignedAt) {
		OrganisationMembershipRole assignment =
				new OrganisationMembershipRole();
		assignment.id = UUID.randomUUID();
		assignment.membershipId = Objects.requireNonNull(
				membership,
				"membership must not be null").getId();
		assignment.role = Objects.requireNonNull(role, "role must not be null");
		assignment.assignedBy = Objects.requireNonNull(
				actorUserId,
				"actorUserId must not be null");
		assignment.assignedAt = Objects.requireNonNull(
				assignedAt,
				"assignedAt must not be null");
		assignment.active = true;
		return assignment;
	}

	public boolean deactivate(Instant deactivatedAt) {
		if (!active) {
			return false;
		}
		active = false;
		this.deactivatedAt = Objects.requireNonNull(
				deactivatedAt,
				"deactivatedAt must not be null");
		if (this.deactivatedAt.isBefore(assignedAt)) {
			throw new IllegalArgumentException(
					"deactivatedAt must not precede assignedAt");
		}
		return true;
	}
}
