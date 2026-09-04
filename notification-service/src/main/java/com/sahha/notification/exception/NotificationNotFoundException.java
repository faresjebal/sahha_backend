package com.sahha.notification.exception;

public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException() {
		super("notification not found");
	}
}
