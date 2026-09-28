package com.sahha.auth.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class AuthPermissions {
    private AuthPermissions() { }
    public static final String ACCOUNT_READ = "auth:account:read:platform";
    public static final String ACCOUNT_STATUS_WRITE = "auth:account:status:platform";
    public static final String SELF_SERVICE = "auth:account:manage:self";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(
            "PLATFORM_ADMIN", Set.of(ACCOUNT_READ, ACCOUNT_STATUS_WRITE)), Map.of(), Set.of(SELF_SERVICE));
    }
}
