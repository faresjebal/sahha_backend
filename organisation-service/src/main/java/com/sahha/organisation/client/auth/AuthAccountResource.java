package com.sahha.organisation.client.auth;

import java.util.UUID;

public record AuthAccountResource(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String status,
		boolean emailVerified) {

	public boolean eligibleForMembership() {
		return "ACTIVE".equals(status) && emailVerified;
	}
}
