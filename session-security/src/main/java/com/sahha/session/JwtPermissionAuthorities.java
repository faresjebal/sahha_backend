package com.sahha.session;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Stateless claim-to-authority mechanics; permission names and role policies belong to each service.
 * Invoke only after JWT and current-session validation. This does not authorise a resource.
 */
public final class JwtPermissionAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final Map<String, Set<String>> platform;
    private final Map<String, Set<String>> organisation;
    private final Set<String> authenticated;

    public JwtPermissionAuthorities(Map<String, Set<String>> platform,
            Map<String, Set<String>> organisation, Set<String> authenticated) {
        this.platform = immutable(platform);
        this.organisation = immutable(organisation);
        this.authenticated = Set.copyOf(authenticated);
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<String> permissions = new TreeSet<>(authenticated);
        add(permissions, jwt.getClaims().get("roles"), platform);
        if (validOrganisation(jwt.getClaims().get("org_id"))) {
            add(permissions, jwt.getClaims().get("org_roles"), organisation);
        }
        // Never trust permission/scope/authority claims or mint ROLE_* from arbitrary strings.
        return permissions.stream().map(value -> (GrantedAuthority) new SimpleGrantedAuthority(value)).toList();
    }

    private static void add(Set<String> result, Object claim, Map<String, Set<String>> policy) {
        if (!(claim instanceof Collection<?> roles)) return;
        for (Object role : roles) {
            if (role instanceof String code) result.addAll(policy.getOrDefault(code, Set.of()));
        }
    }

    private static boolean validOrganisation(Object claim) {
        if (!(claim instanceof String value)) return false;
        try { return UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private static Map<String, Set<String>> immutable(Map<String, Set<String>> source) {
        return source.entrySet().stream().collect(Collectors.toUnmodifiableMap(
                Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
    }
}
