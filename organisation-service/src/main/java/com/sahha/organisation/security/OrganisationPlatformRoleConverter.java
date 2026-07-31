package com.sahha.organisation.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class OrganisationPlatformRoleConverter
		implements Converter<Jwt, Collection<GrantedAuthority>> {

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(
				OrganisationAccessTokenValidator.PLATFORM_ROLES_CLAIM);
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
				.distinct()
				.sorted()
				.map(role -> (GrantedAuthority)
						new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}
}
