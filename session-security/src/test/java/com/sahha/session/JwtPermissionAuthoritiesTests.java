package com.sahha.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;

class JwtPermissionAuthoritiesTests {
    private static final String ORG = "10000000-0000-4000-8000-000000000001";
    private final JwtPermissionAuthorities converter = new JwtPermissionAuthorities(
            Map.of("GLOBAL", Set.of("platform:manage")),
            Map.of("ADMIN", Set.of("admin:read"), "CLINICIAN", Set.of("clinical:read")),
            Set.of("account:self"));

    @Test void unionsExplicitGrantsWithoutRoleOrClientPermissionAuthorities() {
        var jwt = jwt(Map.of("roles", List.of("GLOBAL", "GLOBAL"), "org_id", ORG,
                "org_roles", List.of("ADMIN", "CLINICIAN", "CLINICIAN"),
                "permissions", List.of("forged:all"), "scope", "forged:all", "authorities", List.of("ROLE_ROOT")));
        assertEquals(List.of("account:self", "admin:read", "clinical:read", "platform:manage"), values(jwt));
    }

    @Test void neverMovesRolesAcrossClaimScopes() {
        assertEquals(List.of("account:self"), values(jwt(Map.of(
                "roles", List.of("ADMIN", "CLINICIAN"), "org_roles", List.of("GLOBAL"), "org_id", ORG))));
    }

    @Test void missingOrInvalidOrganisationCannotActivateScopedPermissions() {
        assertEquals(List.of("account:self"), values(jwt(Map.of("org_roles", List.of("CLINICIAN")))));
        for (Object value : List.of("", "bad-id", "1-1-1-1-1", 123, ORG + " ")) {
            assertEquals(List.of("account:self"), values(jwt(Map.of("org_roles", List.of("CLINICIAN"), "org_id", value))));
        }
    }

    @Test void malformedUnknownAndPermissionShapedRolesFailClosed() {
        for (Object roles : List.of("GLOBAL", Map.of("GLOBAL", true), List.of("global", "UNKNOWN", "platform:manage"))) {
            assertEquals(List.of("account:self"), values(jwt(Map.of("roles", roles))));
        }
        var mixed = new ArrayList<>(List.of("UNKNOWN", 1)); mixed.add(null);
        assertEquals(List.of("account:self"), values(jwt(Map.of("roles", mixed))));
    }

    @Test void defensivelyCopiesPoliciesAndReturnsImmutableResults() {
        var grants = new HashSet<>(Set.of("initial:grant"));
        var policy = new HashMap<String, Set<String>>(); policy.put("GLOBAL", grants);
        var original = new JwtPermissionAuthorities(policy, Map.of(), Set.of());
        grants.add("late:grant"); policy.put("ADMIN", Set.of("late:grant"));
        var result = original.convert(jwt(Map.of("roles", List.of("GLOBAL", "ADMIN"))));
        assertEquals(List.of("initial:grant"), result.stream().map(GrantedAuthority::getAuthority).toList());
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    private List<String> values(Jwt jwt) { return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList(); }
    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue("synthetic").header("alg", "RS256").subject("synthetic-user")
                .claims(values -> values.putAll(claims)).build();
    }
}
