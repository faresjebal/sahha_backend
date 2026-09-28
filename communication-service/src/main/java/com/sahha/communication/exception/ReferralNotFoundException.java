package com.sahha.communication.exception;

public class ReferralNotFoundException extends RuntimeException {
	public ReferralNotFoundException() { super("Referral not found"); }
}
