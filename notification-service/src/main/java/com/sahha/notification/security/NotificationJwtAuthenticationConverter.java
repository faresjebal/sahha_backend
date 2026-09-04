package com.sahha.notification.security;

import java.util.Collection;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class NotificationJwtAuthenticationConverter
		implements Converter<Jwt, AbstractAuthenticationToken> {

	private final NotificationRoleConverter roleConverter =
			new NotificationRoleConverter();

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		Collection<GrantedAuthority> authorities = roleConverter.convert(jwt);
		return new JwtAuthenticationToken(
				jwt,
				authorities,
				NotificationPrincipalName.from(jwt));
	}
}
