package com.sahha.scheduling.security;

import java.util.Collection;
import java.util.List;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class SchedulingRoleConverter
		implements Converter<Jwt, Collection<GrantedAuthority>> {

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		List<String> platformRoles = jwt.getClaimAsStringList(
				SchedulingAccessTokenValidator.PLATFORM_ROLES_CLAIM);
		List<String> organisationRoles = jwt.getClaimAsStringList(
				SchedulingAccessTokenValidator.ORGANISATION_ROLES_CLAIM);
		return java.util.stream.Stream.concat(
				platformRoles == null
						? java.util.stream.Stream.empty()
						: platformRoles.stream(),
				organisationRoles == null
						? java.util.stream.Stream.empty()
						: organisationRoles.stream())
				.distinct()
				.sorted()
				.map(role -> (GrantedAuthority)
						new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}
}
