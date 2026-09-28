package com.sahha.communication.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CommunicationPermissionPolicyTests {
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
        assertEquals(Set.of("communication:conversation:read:participant", "communication:conversation:write:participant", "communication:message:stream:participant", "communication:referral:read:participant", "communication:referral:manage:participant", "communication:sharing:decide"), authorities(List.of(), List.of("DOCTOR"), true));
    }

    @Test void receptionistGetsOnlyItsOwnBundle() {
        assertEquals(Set.of(), authorities(List.of(), List.of("RECEPTIONIST"), true));
    }

    @Test void multiRoleMembershipUnionsExplicitBundles() {
        assertEquals(Set.of("communication:conversation:read:participant", "communication:conversation:write:participant", "communication:message:stream:participant", "communication:referral:read:participant", "communication:referral:manage:participant", "communication:sharing:decide"), authorities(List.of("PLATFORM_ADMIN"), List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), true));
    }

    @Test void wrongScopeRolesNeverGrantCapabilities() {
        assertEquals(Set.of(), authorities(List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), List.of("PLATFORM_ADMIN"), true));
    }

    @Test void unknownAndPermissionShapedRolesNeverGrantCapabilities() {
        assertEquals(Set.of(), authorities(List.of("UNKNOWN", "communication:conversation:read:participant", "communication:conversation:write:participant", "communication:message:stream:participant", "communication:referral:read:participant", "communication:referral:manage:participant", "communication:sharing:decide"), List.of("UNKNOWN", "communication:conversation:read:participant", "communication:conversation:write:participant", "communication:message:stream:participant", "communication:referral:read:participant", "communication:referral:manage:participant", "communication:sharing:decide"), true));
    }

    @Test void organisationRolesNeedAnActiveOrganisation() {
        assertEquals(Set.of(), authorities(List.of(), List.of("DOCTOR", "ORGANIZATION_ADMIN", "RECEPTIONIST"), false));
    }

    private Set<String> authorities(List<String> platform, List<String> organisation, boolean active) {
        var builder = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("synthetic-user")
                .claim("roles", platform).claim("org_roles", organisation)
                .claim("permissions", List.of("communication:conversation:read:participant", "communication:conversation:write:participant", "communication:message:stream:participant", "communication:referral:read:participant", "communication:referral:manage:participant", "communication:sharing:decide"))
                .claim("scope", "communication:conversation:read:participant communication:conversation:write:participant communication:message:stream:participant communication:referral:read:participant communication:referral:manage:participant communication:sharing:decide");
        if (active) builder.claim("org_id", "10000000-0000-4000-8000-000000000001");
        return new CommunicationRoleConverter().convert(builder.build()).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
