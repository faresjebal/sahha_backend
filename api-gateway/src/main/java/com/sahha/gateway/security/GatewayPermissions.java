package com.sahha.gateway.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class GatewayPermissions {
    private GatewayPermissions() { }
    public static final String PLATFORM_ROUTE = "gateway:platform:route";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(
            "PLATFORM_ADMIN", Set.of(PLATFORM_ROUTE)), Map.of(), Set.of());
    }
}
