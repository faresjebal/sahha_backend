package com.sahha.communication.security;

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class CommunicationJwtAuthenticationConverter
		implements Converter<Jwt, AbstractAuthenticationToken> {
	private final CommunicationRoleConverter roles = new CommunicationRoleConverter();

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<?> authorities = roles.convert(jwt);
		@SuppressWarnings("unchecked")
		Collection<org.springframework.security.core.GrantedAuthority> granted =
				(Collection<org.springframework.security.core.GrantedAuthority>) authorities;
		return new JwtAuthenticationToken(jwt, granted,
				CommunicationPrincipalName.from(jwt));
	}
}
