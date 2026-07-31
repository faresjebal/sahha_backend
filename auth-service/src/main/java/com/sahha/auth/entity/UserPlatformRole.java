package com.sahha.auth.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "user_platform_role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class UserPlatformRole {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", nullable = false, updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private PlatformRole role;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "assigned_by_user_id", updatable = false)
	@Getter(onMethod_ = @JsonIgnore)
	private UserAccount assignedBy;

	@Column(name = "assigned_at", nullable = false, updatable = false)
	private Instant assignedAt;

	@Column(nullable = false)
	@ToString.Include
	private boolean active;

	@Column(name = "deactivated_at")
	private Instant deactivatedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static UserPlatformRole assign(
			UserAccount user,
			PlatformRole role,
			UserAccount assignedBy) {
		UserPlatformRole assignment = new UserPlatformRole();
		assignment.id = UUID.randomUUID();
		assignment.user = Objects.requireNonNull(user, "user must not be null");
		assignment.role = Objects.requireNonNull(role, "role must not be null");
		assignment.assignedBy = assignedBy;
		assignment.active = true;
		return assignment;
	}

	public void deactivate(Instant deactivatedAt) {
		if (!active) {
			return;
		}
		this.active = false;
		this.deactivatedAt = Objects.requireNonNull(
				deactivatedAt,
				"deactivatedAt must not be null");
	}

	@PrePersist
	void beforeInsert() {
		Instant now = Instant.now();
		if (assignedAt == null) {
			assignedAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void beforeUpdate() {
		updatedAt = Instant.now();
	}

}
