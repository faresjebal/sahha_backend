package com.sahha.patient.client.auth;

import java.util.UUID;

public record AuthAccountResource(
		UUID id,
		String email,
		String firstName,
		String lastName,
		String status,
		boolean emailVerified) {

	public boolean eligibleForPatientLink() {
		return "ACTIVE".equals(status) && emailVerified;
	}
}
