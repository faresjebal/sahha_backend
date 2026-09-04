package com.sahha.notification.dto.response;

import java.util.List;

public record NotificationPageResponse(
		List<NotificationResponse> items,
		int page,
		int size,
		long totalElements,
		int totalPages,
		boolean first,
		boolean last) {

	public NotificationPageResponse {
		items = List.copyOf(items);
	}
}
