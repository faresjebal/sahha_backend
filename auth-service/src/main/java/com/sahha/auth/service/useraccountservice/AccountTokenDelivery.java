package com.sahha.auth.service.useraccountservice;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sahha.auth.service.verificationtokenservice.IssuedVerificationToken;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString(onlyExplicitlyIncluded = true)
public final class AccountTokenDelivery {

	@Getter(onMethod_ = @JsonIgnore)
	private final String recipientEmail;

	@Getter(onMethod_ = @JsonIgnore)
	private final String recipientFirstName;

	@Getter(onMethod_ = @JsonIgnore)
	private final IssuedVerificationToken issuedToken;

	public AccountTokenDelivery(
			String recipientEmail,
			String recipientFirstName,
			IssuedVerificationToken issuedToken) {
		this.recipientEmail = requireText(
				recipientEmail,
				"recipientEmail");
		this.recipientFirstName = requireText(
				recipientFirstName,
				"recipientFirstName");
		this.issuedToken = Objects.requireNonNull(
				issuedToken,
				"issuedToken must not be null");
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " must not be blank");
		}
		return value.strip();
	}
}
