package com.sahha.notification.controller;

import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.notification.dto.response.MarkAllNotificationsReadResponse;
import com.sahha.notification.dto.response.NotificationPageResponse;
import com.sahha.notification.dto.response.NotificationResponse;
import com.sahha.notification.dto.response.UnreadNotificationCountResponse;
import com.sahha.notification.exception.NotificationAccessDeniedException;
import com.sahha.notification.security.NotificationAccessTokenValidator;
import com.sahha.notification.service.inappnotificationservice.NotificationInboxService;

@RestController
@Validated
@RequestMapping("/api/v1/notifications")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Notifications",
		description = "Private in-app notifications for the signed-in user and active organisation.")
public class NotificationController {

	private final NotificationInboxService inboxService;

	public NotificationController(NotificationInboxService inboxService) {
		this.inboxService = inboxService;
	}

	@GetMapping
	@Operation(
			operationId = "listNotifications",
			summary = "List the signed-in user's notification history")
	public ResponseEntity<NotificationPageResponse> list(
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(inboxService.list(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						page,
						size));
	}

	@GetMapping("/unread-count")
	@Operation(
			operationId = "getUnreadNotificationCount",
			summary = "Count the signed-in user's unread notifications")
	public ResponseEntity<UnreadNotificationCountResponse> unreadCount(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(inboxService.unreadCount(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	@PostMapping("/{notificationId}/read")
	@Operation(
			operationId = "markNotificationRead",
			summary = "Mark one owned notification as read")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<NotificationResponse> markRead(
			@PathVariable UUID notificationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(inboxService.markRead(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue(),
						notificationId));
	}

	@PostMapping("/read-all")
	@Operation(
			operationId = "markAllNotificationsRead",
			summary = "Mark all owned notifications in the active organisation as read")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<MarkAllNotificationsReadResponse> markAllRead(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(inboxService.markAllRead(
						activeOrganisationId(jwt),
						actorUserId(jwt),
						jwt.getTokenValue()));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new NotificationAccessDeniedException();
		}
	}

	private static UUID activeOrganisationId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getClaimAsString(
					NotificationAccessTokenValidator.ACTIVE_ORGANISATION_ID_CLAIM));
		}
		catch (RuntimeException invalidContext) {
			throw new NotificationAccessDeniedException();
		}
	}
}
