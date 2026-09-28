package com.sahha.notification.patient;

import java.time.Clock;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.sahha.notification.dto.response.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@Validated
@SecurityRequirement(name = "cookieAuth")
@RequestMapping("/api/v1/notifications/patient/registrations/{registrationId}")
public class PatientNotificationController {
    private final PatientNotificationAccess access;
    private final PatientNotificationRepository repository;
    private final Clock clock;
    public PatientNotificationController(PatientNotificationAccess access, PatientNotificationRepository repository, Clock clock) {
        this.access = access; this.repository = repository; this.clock = clock;
    }

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(@PathVariable UUID registrationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, @AuthenticationPrincipal Jwt jwt) {
        return own(repository.list(access.requireOwnRegistration(registrationId, jwt), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadNotificationCountResponse> unread(@PathVariable UUID registrationId, @AuthenticationPrincipal Jwt jwt) {
        return own(new UnreadNotificationCountResponse(repository.unread(access.requireOwnRegistration(registrationId, jwt))));
    }

    @PostMapping("/{id}/read")
    @SecurityRequirement(name = "csrfHeader")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID registrationId, @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return own(repository.markRead(access.requireOwnRegistration(registrationId, jwt), id, clock.instant()));
    }

    @PostMapping("/read-all")
    @SecurityRequirement(name = "csrfHeader")
    public ResponseEntity<MarkAllNotificationsReadResponse> markAllRead(@PathVariable UUID registrationId,
            @AuthenticationPrincipal Jwt jwt) {
        return own(repository.markAllRead(access.requireOwnRegistration(registrationId, jwt), clock.instant()));
    }

    private static <T> ResponseEntity<T> own(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
