package com.sahha.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.web.csrf.CsrfToken;

@Schema(
		name = "CsrfTokenResponse",
		description = """
				The browser must echo token in headerName for every state-changing \
				request. The same token is also written to the readable XSRF cookie.
				""")
public record CsrfTokenResponse(
		@Schema(example = "X-XSRF-TOKEN")
		String headerName,

		@Schema(example = "_csrf")
		String parameterName,

		@Schema(example = "synthetic-csrf-token")
		String token) {

	public static CsrfTokenResponse from(CsrfToken csrfToken) {
		return new CsrfTokenResponse(
				csrfToken.getHeaderName(),
				csrfToken.getParameterName(),
				csrfToken.getToken());
	}
}
