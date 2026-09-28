package com.sahha.communication.exception;

public class ReferralStateConflictException extends RuntimeException {
	public ReferralStateConflictException() { super("Referral state transition is not allowed"); }
}
