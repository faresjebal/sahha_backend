package com.sahha.auth.security;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientNetworkAddressResolver {

	public static final String TRUSTED_GATEWAY_HEADER = "X-Sahha-Client-IP";

	private static final Pattern NUMERIC_ADDRESS =
			Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

	public String resolve(HttpServletRequest request) {
		String forwardedByGateway = request.getHeader(TRUSTED_GATEWAY_HEADER);
		if (isNumericAddress(forwardedByGateway)) {
			return forwardedByGateway.strip();
		}
		String remoteAddress = request.getRemoteAddr();
		return isNumericAddress(remoteAddress)
				? remoteAddress.strip()
				: "unavailable";
	}

	private static boolean isNumericAddress(String candidate) {
		return candidate != null
				&& NUMERIC_ADDRESS.matcher(candidate.strip()).matches()
				&& (candidate.contains(".") || candidate.contains(":"));
	}
}
