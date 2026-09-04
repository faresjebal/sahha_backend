package com.sahha.notification.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.sahha.notification.dto.response.NotificationPageResponse;
import com.sahha.notification.dto.response.NotificationResponse;
import com.sahha.notification.entity.InAppNotification;

@Component
public class NotificationMapper {

	public NotificationResponse toResponse(InAppNotification notification) {
		return new NotificationResponse(
				notification.getId(),
				notification.getNotificationType(),
				notification.getResourceType(),
				notification.getResourceId(),
				notification.getAppointmentStatus(),
				notification.getAppointmentStartsAt(),
				notification.getAppointmentEndsAt(),
				notification.getAppointmentTimeZone(),
				notification.getAppointmentLocationLabel(),
				notification.getResourceVersion(),
				notification.getEventOccurredAt(),
				notification.getCreatedAt(),
				notification.getReadAt() != null,
				notification.getReadAt());
	}

	public NotificationPageResponse toPage(
			Page<InAppNotification> notifications) {
		return new NotificationPageResponse(
				notifications.getContent().stream()
						.map(this::toResponse)
						.toList(),
				notifications.getNumber(),
				notifications.getSize(),
				notifications.getTotalElements(),
				notifications.getTotalPages(),
				notifications.isFirst(),
				notifications.isLast());
	}
}
