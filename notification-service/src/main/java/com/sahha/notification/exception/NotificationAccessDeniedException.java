package com.sahha.notification.exception;

public class NotificationAccessDeniedException extends RuntimeException {

	public NotificationAccessDeniedException() {
		super("notification access denied");
	}
}
