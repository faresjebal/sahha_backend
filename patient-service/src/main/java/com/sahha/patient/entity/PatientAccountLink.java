package com.sahha.patient.entity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "patient_account_link")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class PatientAccountLink {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(name = "patient_id", nullable = false, updatable = false)
	private UUID patientId;

	@Column(name = "auth_user_id", nullable = false, updatable = false)
	@ToString.Include
	private UUID authUserId;

	@Column(name = "linked_by_user_id", nullable = false, updatable = false)
	private UUID linkedByUserId;

	@Column(name = "linked_at", nullable = false, updatable = false)
	private Instant linkedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static PatientAccountLink link(
			UUID patientId,
			UUID authUserId,
			Instant linkedAt) {
		PatientAccountLink link = new PatientAccountLink();
		link.id = UUID.randomUUID();
		link.patientId = Objects.requireNonNull(patientId, "patientId must not be null");
		link.authUserId = Objects.requireNonNull(authUserId, "authUserId must not be null");
		link.linkedByUserId = authUserId;
		link.linkedAt = Objects.requireNonNull(linkedAt, "linkedAt must not be null");
		link.updatedAt = linkedAt;
		return link;
	}
}
