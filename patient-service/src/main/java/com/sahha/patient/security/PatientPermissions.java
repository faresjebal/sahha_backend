package com.sahha.patient.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class PatientPermissions {
    private PatientPermissions() { }
    public static final String ADMIN_READ = "patient:administrative:read";
    public static final String ADMIN_WRITE = "patient:administrative:write";
    public static final String SELF_LINK = "patient:registration:link:self";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(), Map.of(
            "ORGANIZATION_ADMIN", Set.of(ADMIN_READ, ADMIN_WRITE),
            "RECEPTIONIST", Set.of(ADMIN_READ, ADMIN_WRITE)), Set.of(SELF_LINK));
    }
}
