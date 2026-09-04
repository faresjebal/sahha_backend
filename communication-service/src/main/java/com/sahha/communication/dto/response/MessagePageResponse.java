package com.sahha.communication.dto.response;

import java.util.List;

public record MessagePageResponse(
		List<MessageResponse> content, int page, int size,
		long totalElements, int totalPages) {
	public MessagePageResponse { content = List.copyOf(content); }
}
