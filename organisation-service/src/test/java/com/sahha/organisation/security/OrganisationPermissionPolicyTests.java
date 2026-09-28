package com.sahha.organisation.security;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OrganisationPermissionPolicyTests {
    @Test void noAssignedRolesGetsOnlyOwnAccountCapabilities() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self"), authorities(List.of(), List.of(), true));
    }

    @Test void explicitPlatformRoleGrantsOnlyPlatformCapabilities() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self", "organisation:read:platform", "organisation:manage:platform"), authorities(List.of("PLATFORM_ADMIN"), List.of(), true));
    }

    @Test void organization_adminGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self", "organisation:profile:manage", "organisation:departments:manage", "organisation:staff:manage", "organisation:invitations:manage"), authorities(List.of(), List.of("ORGANIZATION_ADMIN"), true));
    }

    @Test void doctorGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self", "organisation:doctor-profile:manage:self", "organisation:colleagues:read"), authorities(List.of(), List.of("DOCTOR"), true));
    }

    @Test void receptionistGetsOnlyItsOwnBundle() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self"), authorities(List.of(), List.of("RECEPTIONIST"), true));
    }

    @Test void multiRoleMembershipUnionsExplicitBundles() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self", "organisation:read:platform", "organisation:manage:platform", "organisation:profile:manage", "organisation:departments:manage", "organisation:staff:manage", "organisation:invitations:manage", "organisation:doctor-profile:manage:self", "organisation:colleagues:read"), authorities(List.of("PLATFORM_ADMIN"), List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), true));
    }

    @Test void wrongScopeRolesNeverGrantCapabilities() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self"), authorities(List.of("ORGANIZATION_ADMIN", "DOCTOR", "RECEPTIONIST"), List.of("PLATFORM_ADMIN"), true));
    }

    @Test void unknownAndPermissionShapedRolesNeverGrantCapabilities() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self"), authorities(List.of("UNKNOWN", "organisation:read:platform", "organisation:manage:platform", "organisation:profile:manage", "organisation:departments:manage", "organisation:staff:manage", "organisation:invitations:manage", "organisation:doctor-profile:manage:self", "organisation:colleagues:read", "organisation:context:read:self", "organisation:invitations:respond:self"), List.of("UNKNOWN", "organisation:read:platform", "organisation:manage:platform", "organisation:profile:manage", "organisation:departments:manage", "organisation:staff:manage", "organisation:invitations:manage", "organisation:doctor-profile:manage:self", "organisation:colleagues:read", "organisation:context:read:self", "organisation:invitations:respond:self"), true));
    }

    @Test void organisationRolesNeedAnActiveOrganisation() {
        assertEquals(Set.of("organisation:context:read:self", "organisation:invitations:respond:self"), authorities(List.of(), List.of("DOCTOR", "ORGANIZATION_ADMIN", "RECEPTIONIST"), false));
    }

    private Set<String> authorities(List<String> platform, List<String> organisation, boolean active) {
        var builder = Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("synthetic-user")
                .claim("roles", platform).claim("org_roles", organisation)
                .claim("permissions", List.of("organisation:read:platform", "organisation:manage:platform", "organisation:profile:manage", "organisation:departments:manage", "organisation:staff:manage", "organisation:invitations:manage", "organisation:doctor-profile:manage:self", "organisation:colleagues:read", "organisation:context:read:self", "organisation:invitations:respond:self"))
                .claim("scope", "organisation:read:platform organisation:manage:platform organisation:profile:manage organisation:departments:manage organisation:staff:manage organisation:invitations:manage organisation:doctor-profile:manage:self organisation:colleagues:read organisation:context:read:self organisation:invitations:respond:self");
        if (active) builder.claim("org_id", "10000000-0000-4000-8000-000000000001");
        return new OrganisationPlatformRoleConverter().convert(builder.build()).stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
