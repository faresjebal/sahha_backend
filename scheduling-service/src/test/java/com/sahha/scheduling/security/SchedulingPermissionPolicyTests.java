package com.sahha.scheduling.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulingPermissionPolicyTests {
    @Test void noAssignedRolesGetsOnlyOwnAccountCapabilities() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self"), authorities(List.of(), List.of(), true));
    }

    @Test void explicitPlatformRoleGrantsOnlyPlatformCapabilities() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self"), authorities(List.of("PLATFORM_ADMIN"), List.of(), true));
    }

    @Test void organization_adminGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in"), authorities(List.of(), List.of("ORGANIZATION_ADMIN"), true));
    }

    @Test void doctorGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self", "scheduling:availability:manage:self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:adjust", "scheduling:appointment:respond", "scheduling:appointment:treat", "scheduling:clinical-context:read"), authorities(List.of(), List.of("DOCTOR"), true));
    }

    @Test void receptionistGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in"), authorities(List.of(), List.of("RECEPTIONIST"), true));
    }

    @Test void multiRoleMembershipUnionsExplicitBundles() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in", "scheduling:availability:manage:self", "scheduling:appointment:respond", "scheduling:appointment:treat", "scheduling:clinical-context:read"), authorities(List.of("PLATFORM_ADMIN"), List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), true));
    }

    @Test void wrongScopeRolesNeverGrantCapabilities() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self"), authorities(List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), List.of("PLATFORM_ADMIN"), true));
    }

    @Test void unknownAndPermissionShapedRolesNeverGrantCapabilities() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self"), authorities(List.of("UNKNOWN", "scheduling:availability:manage:self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in", "scheduling:appointment:respond", "scheduling:appointment:treat", "scheduling:clinical-context:read", "scheduling:appointment:manage:patient-self"), List.of("UNKNOWN", "scheduling:availability:manage:self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in", "scheduling:appointment:respond", "scheduling:appointment:treat", "scheduling:clinical-context:read", "scheduling:appointment:manage:patient-self"), true));
    }

    @Test void organisationRolesNeedAnActiveOrganisation() {
        assertEquals(Set.of("scheduling:appointment:manage:patient-self"), authorities(List.of(), List.of("DOCTOR", "ORGANIZATION_ADMIN", "RECEPTIONIST"), false));
    }

    private Set<String> authorities(List<String> platform, List<String> organisation, boolean active) {
        var builder = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("synthetic-user")
                .claim("roles", platform).claim("org_roles", organisation)
                .claim("permissions", List.of("scheduling:availability:manage:self", "scheduling:availability:read", "scheduling:appointment:read", "scheduling:appointment:book", "scheduling:appointment:adjust", "scheduling:appointment:check-in", "scheduling:appointment:respond", "scheduling:appointment:treat", "scheduling:clinical-context:read", "scheduling:appointment:manage:patient-self"))
                .claim("scope", "scheduling:availability:manage:self scheduling:availability:read scheduling:appointment:read scheduling:appointment:book scheduling:appointment:adjust scheduling:appointment:check-in scheduling:appointment:respond scheduling:appointment:treat scheduling:clinical-context:read scheduling:appointment:manage:patient-self");
        if (active) builder.claim("org_id", "10000000-0000-4000-8000-000000000001");
        return new SchedulingRoleConverter().convert(builder.build()).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
