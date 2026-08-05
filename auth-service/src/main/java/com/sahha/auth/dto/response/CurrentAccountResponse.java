package com.sahha.auth.dto.response;

import java.util.UUID;

import com.sahha.auth.entity.AccountStatus;
import com.sahha.auth.entity.UserAccount;

public record CurrentAccountResponse(
		UUID id,
		String email,
		String firstName,
		String lastName,
		AccountStatus status,
		boolean emailVerified) {

	public static CurrentAccountResponse from(UserAccount account) {
		return new CurrentAccountResponse(
				account.getId(),
				account.getEmail(),
				account.getFirstName(),
				account.getLastName(),
				account.getStatus(),
				account.getEmailVerifiedAt() != null);
	}
}
