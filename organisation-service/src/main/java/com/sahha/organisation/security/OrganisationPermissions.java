package com.sahha.organisation.security;

import java.util.Map;
import java.util.Set;
import com.sahha.session.JwtPermissionAuthorities;

/** V1 operation permissions owned by this service. Resource-level checks remain mandatory. */
public final class OrganisationPermissions {
    private OrganisationPermissions() { }
    public static final String PLATFORM_READ = "organisation:read:platform";
    public static final String PLATFORM_MANAGE = "organisation:manage:platform";
    public static final String PROFILE_MANAGE = "organisation:profile:manage";
    public static final String DEPARTMENTS_MANAGE = "organisation:departments:manage";
    public static final String STAFF_MANAGE = "organisation:staff:manage";
    public static final String INVITATIONS_MANAGE = "organisation:invitations:manage";
    public static final String DOCTOR_PROFILE_SELF = "organisation:doctor-profile:manage:self";
    public static final String COLLEAGUES_READ = "organisation:colleagues:read";
    public static final String CONTEXT_READ = "organisation:context:read:self";
    public static final String INVITATIONS_SELF = "organisation:invitations:respond:self";

    public static JwtPermissionAuthorities authorities() {
        return new JwtPermissionAuthorities(Map.of(
            "PLATFORM_ADMIN", Set.of(PLATFORM_READ, PLATFORM_MANAGE)), Map.of(
            "ORGANIZATION_ADMIN", Set.of(PROFILE_MANAGE, DEPARTMENTS_MANAGE, STAFF_MANAGE, INVITATIONS_MANAGE),
            "DOCTOR", Set.of(DOCTOR_PROFILE_SELF, COLLEAGUES_READ)), Set.of(CONTEXT_READ, INVITATIONS_SELF));
    }
}
