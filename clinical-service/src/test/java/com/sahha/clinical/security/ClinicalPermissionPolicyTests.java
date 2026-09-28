package com.sahha.clinical.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClinicalPermissionPolicyTests {
    @Test void noAssignedRolesGetsOnlyOwnAccountCapabilities() {
        assertEquals(Set.of(), authorities(List.of(), List.of(), true));
    }

    @Test void explicitPlatformRoleGrantsOnlyPlatformCapabilities() {
        assertEquals(Set.of(), authorities(List.of("PLATFORM_ADMIN"), List.of(), true));
    }

    @Test void organization_adminGetsOnlyItsOwnBundle() {
        assertEquals(Set.of(), authorities(List.of(), List.of("ORGANIZATION_ADMIN"), true));
    }

    @Test void doctorGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("clinical:record:read:own", "clinical:draft:write:own", "clinical:record:finalize:own", "clinical:record:correct:own", "clinical:summary:read:care", "clinical:selected-resource:read:shared", "clinical:history:read:shared-care", "clinical:attachment-context:read:own"), authorities(List.of(), List.of("DOCTOR"), true));
    }

    @Test void receptionistGetsOnlyItsOwnBundle() {
        assertEquals(Set.of(), authorities(List.of(), List.of("RECEPTIONIST"), true));
    }

    @Test void multiRoleMembershipUnionsExplicitBundles() {
        assertEquals(Set.of("clinical:record:read:own", "clinical:draft:write:own", "clinical:record:finalize:own", "clinical:record:correct:own", "clinical:summary:read:care", "clinical:selected-resource:read:shared", "clinical:history:read:shared-care", "clinical:attachment-context:read:own"), authorities(List.of("PLATFORM_ADMIN"), List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), true));
    }

    @Test void wrongScopeRolesNeverGrantCapabilities() {
        assertEquals(Set.of(), authorities(List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), List.of("PLATFORM_ADMIN"), true));
    }

    @Test void unknownAndPermissionShapedRolesNeverGrantCapabilities() {
        assertEquals(Set.of(), authorities(List.of("UNKNOWN", "clinical:record:read:own", "clinical:draft:write:own", "clinical:record:finalize:own", "clinical:record:correct:own", "clinical:summary:read:care", "clinical:selected-resource:read:shared", "clinical:attachment-context:read:own"), List.of("UNKNOWN", "clinical:record:read:own", "clinical:draft:write:own", "clinical:record:finalize:own", "clinical:record:correct:own", "clinical:summary:read:care", "clinical:selected-resource:read:shared", "clinical:attachment-context:read:own"), true));
    }

    @Test void organisationRolesNeedAnActiveOrganisation() {
        assertEquals(Set.of(), authorities(List.of(), List.of("DOCTOR", "ORGANIZATION_ADMIN", "RECEPTIONIST"), false));
    }

    private Set<String> authorities(List<String> platform, List<String> organisation, boolean active) {
        var builder = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("synthetic-user")
                .claim("roles", platform).claim("org_roles", organisation)
                .claim("permissions", List.of("clinical:record:read:own", "clinical:draft:write:own", "clinical:record:finalize:own", "clinical:record:correct:own", "clinical:summary:read:care", "clinical:selected-resource:read:shared", "clinical:attachment-context:read:own"))
                .claim("scope", "clinical:record:read:own clinical:draft:write:own clinical:record:finalize:own clinical:record:correct:own clinical:summary:read:care clinical:selected-resource:read:shared clinical:attachment-context:read:own");
        if (active) builder.claim("org_id", "10000000-0000-4000-8000-000000000001");
        return new ClinicalRoleConverter().convert(builder.build()).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
