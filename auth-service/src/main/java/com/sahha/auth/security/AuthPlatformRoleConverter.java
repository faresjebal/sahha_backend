package com.sahha.auth.security;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class AuthPlatformRoleConverter
		implements Converter<Jwt, Collection<GrantedAuthority>> {

	private static final Pattern ROLE_CODE =
			Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList(
				SessionBoundJwtValidator.PLATFORM_ROLES_CLAIM);
		if (roles == null) {
			return List.of();
		}
		return roles.stream()
				.filter(role -> role != null && ROLE_CODE.matcher(role).matches())
				.distinct()
				.sorted()
				.map(role -> (GrantedAuthority)
						new SimpleGrantedAuthority("ROLE_" + role))
				.toList();
	}
}
