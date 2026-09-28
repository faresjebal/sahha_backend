package com.sahha.auth.security;

import java.util.Collection;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import com.sahha.session.JwtPermissionAuthorities;

/** Converts validated role claims to service-owned permissions, not Spring role authorities. */
public final class AuthPlatformRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final JwtPermissionAuthorities permissions = AuthPermissions.authorities();

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return permissions.convert(jwt);
    }
}
