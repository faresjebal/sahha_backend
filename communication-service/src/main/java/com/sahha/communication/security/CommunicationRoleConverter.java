package com.sahha.communication.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CommunicationRoleConverter
		implements Converter<Jwt, Collection<GrantedAuthority>> {
	@Override
	public Collection<GrantedAuthority> convert(Jwt jwt) {
		List<String> platform = jwt.getClaimAsStringList(
				CommunicationAccessTokenValidator.PLATFORM_ROLES_CLAIM);
		List<String> organisation = jwt.getClaimAsStringList(
				CommunicationAccessTokenValidator.ORGANISATION_ROLES_CLAIM);
		return Stream.concat(platform == null ? Stream.empty() : platform.stream(),
				organisation == null ? Stream.empty() : organisation.stream())
				.distinct().sorted()
				.map(value -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + value))
				.toList();
	}
}
