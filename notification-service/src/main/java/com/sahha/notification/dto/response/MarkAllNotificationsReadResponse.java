package com.sahha.notification.dto.response;

import java.time.Instant;

public record MarkAllNotificationsReadResponse(
		int updatedCount,
		Instant readAt) {
}
