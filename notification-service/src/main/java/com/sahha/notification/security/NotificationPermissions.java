package com.sahha.notification.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class NotificationPermissions {
    private NotificationPermissions() { }
    public static final String READ_SELF = "notification:read:self";
    public static final String UPDATE_SELF = "notification:update:self";
    public static final String STREAM_SELF = "notification:stream:self";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(), Set.of(READ_SELF, UPDATE_SELF, STREAM_SELF));
    }
}
