package com.sahha.communication.exception;

public class ReferralVersionConflictException extends RuntimeException {
	public ReferralVersionConflictException() { super("Referral version is stale"); }
}
