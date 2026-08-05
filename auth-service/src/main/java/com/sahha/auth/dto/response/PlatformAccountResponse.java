package com.sahha.auth.dto.response;

import java.util.UUID;

import com.sahha.auth.entity.AccountStatus;

public record PlatformAccountResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		AccountStatus status,
		boolean emailVerified) {
}
