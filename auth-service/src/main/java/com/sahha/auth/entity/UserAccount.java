package com.sahha.auth.entity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "user_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(onlyExplicitlyIncluded = true)
public class UserAccount {

	@Id
	@Column(nullable = false, updatable = false)
	@ToString.Include
	private UUID id;

	@Column(nullable = false, length = 320)
	private String email;

	@Column(name = "normalized_email", nullable = false, length = 320)
	private String normalizedEmail;

	@Column(name = "password_hash", nullable = false, length = 255)
	@Getter(onMethod_ = @JsonIgnore)
	private String passwordHash;

	@Column(name = "first_name", nullable = false, length = 100)
	private String firstName;

	@Column(name = "last_name", nullable = false, length = 100)
	private String lastName;

	@Column(name = "phone_number", length = 32)
	private String phoneNumber;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	@ToString.Include
	private AccountStatus status;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "password_changed_at", nullable = false)
	private Instant passwordChangedAt;

	@Column(name = "credential_version", nullable = false)
	private int credentialVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	public static UserAccount pendingRegistration(
			String email,
			String normalizedEmail,
			String passwordHash,
			String firstName,
			String lastName,
			String phoneNumber) {
		return pendingRegistration(
				email,
				normalizedEmail,
				passwordHash,
				firstName,
				lastName,
				phoneNumber,
				Instant.now());
	}

	public static UserAccount pendingRegistration(
			String email,
			String normalizedEmail,
			String passwordHash,
			String firstName,
			String lastName,
			String phoneNumber,
			Instant registeredAt) {
		Instant requiredRegisteredAt = Objects.requireNonNull(
				registeredAt,
				"registeredAt must not be null");
		UserAccount account = new UserAccount();
		account.id = UUID.randomUUID();
		account.email = requireText(email, "email");
		account.normalizedEmail = requireText(normalizedEmail, "normalizedEmail");
		account.passwordHash = requireText(passwordHash, "passwordHash");
		account.firstName = requireText(firstName, "firstName");
		account.lastName = requireText(lastName, "lastName");
		account.phoneNumber = normalizeOptional(phoneNumber);
		account.status = AccountStatus.PENDING_VERIFICATION;
		account.failedLoginAttempts = 0;
		account.credentialVersion = 1;
		account.passwordChangedAt = requiredRegisteredAt;
		account.createdAt = requiredRegisteredAt;
		account.updatedAt = requiredRegisteredAt;
		return account;
	}

	public void verifyEmail(Instant verifiedAt) {
		if (emailVerifiedAt != null) {
			return;
		}
		if (status != AccountStatus.PENDING_VERIFICATION) {
			throw new IllegalStateException(
					"only a pending account can verify its email");
		}
		this.emailVerifiedAt = requireNotBeforeCreation(
				verifiedAt,
				"verifiedAt");
		this.status = AccountStatus.ACTIVE;
	}

	public void updateProfile(String firstName, String lastName, String phoneNumber, Instant changedAt) {
		if (!canAuthenticate()) throw new IllegalStateException("account is not active");
		this.firstName = requireText(firstName, "firstName");
		this.lastName = requireText(lastName, "lastName");
		this.phoneNumber = normalizeOptional(phoneNumber);
		this.updatedAt = requireNotBeforeCreation(changedAt, "changedAt");
	}

	public void releaseExpiredLock(Instant observedAt) {
		Instant requiredObservedAt = Objects.requireNonNull(
				observedAt,
				"observedAt must not be null");
		if (status == AccountStatus.LOCKED
				&& lockedUntil != null
				&& !requiredObservedAt.isBefore(lockedUntil)) {
			status = AccountStatus.ACTIVE;
			failedLoginAttempts = 0;
			lockedUntil = null;
		}
	}

	public boolean canAuthenticate() {
		return status == AccountStatus.ACTIVE;
	}

	public void recordFailedLogin(
			Instant attemptedAt,
			int maximumFailedAttempts,
			Duration lockDuration) {
		if (status != AccountStatus.ACTIVE) {
			throw new IllegalStateException(
					"failed login attempts can only be recorded for active accounts");
		}
		if (maximumFailedAttempts < 1) {
			throw new IllegalArgumentException(
					"maximumFailedAttempts must be at least one");
		}
		Duration requiredLockDuration = Objects.requireNonNull(
				lockDuration,
				"lockDuration must not be null");
		if (requiredLockDuration.isZero() || requiredLockDuration.isNegative()) {
			throw new IllegalArgumentException("lockDuration must be positive");
		}
		Instant requiredAttemptedAt = requireNotBeforeCreation(
				attemptedAt,
				"attemptedAt");

		failedLoginAttempts = Math.addExact(failedLoginAttempts, 1);
		if (failedLoginAttempts >= maximumFailedAttempts) {
			status = AccountStatus.LOCKED;
			lockedUntil = requiredAttemptedAt.plus(requiredLockDuration);
		}
	}

	public void recordSuccessfulLogin(Instant loggedInAt) {
		if (status != AccountStatus.ACTIVE) {
			throw new IllegalStateException(
					"successful login requires an active account");
		}
		lastLoginAt = requireNotBeforeCreation(loggedInAt, "loggedInAt");
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public void changePassword(String newPasswordHash, Instant changedAt) {
		if (status == AccountStatus.DISABLED) {
			throw new IllegalStateException(
					"a disabled account cannot change its password");
		}
		passwordHash = requireText(newPasswordHash, "newPasswordHash");
		passwordChangedAt = requireNotBeforeCreation(changedAt, "changedAt");
		credentialVersion = Math.addExact(credentialVersion, 1);
		failedLoginAttempts = 0;
		lockedUntil = null;
		if (status == AccountStatus.LOCKED) {
			status = AccountStatus.ACTIVE;
		}
	}

	public void suspend() {
		if (status == AccountStatus.DISABLED) {
			throw new IllegalStateException("a disabled account cannot be suspended");
		}
		if (status == AccountStatus.SUSPENDED) {
			return;
		}
		status = AccountStatus.SUSPENDED;
		credentialVersion = Math.addExact(credentialVersion, 1);
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	public void reactivate() {
		if (status != AccountStatus.SUSPENDED || emailVerifiedAt == null) {
			throw new IllegalStateException(
					"only a verified suspended account can be reactivated");
		}
		status = AccountStatus.ACTIVE;
		credentialVersion = Math.addExact(credentialVersion, 1);
	}

	public void disable() {
		if (status == AccountStatus.DISABLED) {
			return;
		}
		status = AccountStatus.DISABLED;
		credentialVersion = Math.addExact(credentialVersion, 1);
		failedLoginAttempts = 0;
		lockedUntil = null;
	}

	@PrePersist
	void beforeInsert() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (passwordChangedAt == null) {
			passwordChangedAt = createdAt;
		}
		if (updatedAt == null) {
			updatedAt = createdAt;
		}
	}

	@PreUpdate
	void beforeUpdate() {
		updatedAt = Instant.now();
	}

	private static String requireText(String value, String fieldName) {
		Objects.requireNonNull(value, fieldName + " must not be null");
		String trimmed = value.strip();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return trimmed;
	}

	private static String normalizeOptional(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private Instant requireNotBeforeCreation(Instant value, String fieldName) {
		Instant required = Objects.requireNonNull(
				value,
				fieldName + " must not be null");
		if (required.isBefore(createdAt)) {
			throw new IllegalArgumentException(
					fieldName + " must not precede account creation");
		}
		return required;
	}

}
