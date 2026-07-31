package com.sahha.auth.controller;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.dto.request.LoginRequest;
import com.sahha.auth.dto.response.AuthenticatedSessionResponse;
import com.sahha.auth.dto.response.CsrfTokenResponse;
import com.sahha.auth.exception.InvalidRefreshTokenException;
import com.sahha.auth.security.ClientNetworkAddressResolver;
import com.sahha.auth.security.SessionBoundJwtValidator;
import com.sahha.auth.service.browsersessionservice.BrowserSessionCookieService;
import com.sahha.auth.service.ratelimitservice.AuthRateLimitScope;
import com.sahha.auth.service.ratelimitservice.AuthRateLimitService;
import com.sahha.auth.service.usersessionservice.BrowserAuthenticationService;
import com.sahha.auth.service.usersessionservice.IssuedBrowserSession;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
		name = "Browser sessions",
		description = """
				Cookie-authenticated browser session lifecycle. First obtain a CSRF \
				token, then echo it in the X-XSRF-TOKEN header on every POST.
				""")
public class BrowserAuthController {

	private static final int MAXIMUM_USER_AGENT_LENGTH = 512;

	private final BrowserAuthenticationService authenticationService;
	private final BrowserSessionCookieService cookieService;
	private final AuthRateLimitService rateLimitService;
	private final ClientNetworkAddressResolver clientAddressResolver;
	private final Clock clock;

	public BrowserAuthController(
			BrowserAuthenticationService authenticationService,
			BrowserSessionCookieService cookieService,
			AuthRateLimitService rateLimitService,
			ClientNetworkAddressResolver clientAddressResolver,
			Clock clock) {
		this.authenticationService = authenticationService;
		this.cookieService = cookieService;
		this.rateLimitService = rateLimitService;
		this.clientAddressResolver = clientAddressResolver;
		this.clock = clock;
	}

	@GetMapping("/csrf")
	@Operation(
			operationId = "getCsrfToken",
			summary = "Obtain the browser CSRF token",
			description = """
					Call at application startup and after authentication or logout. The \
					response token is also written to the readable XSRF-TOKEN cookie.
					""")
	public ResponseEntity<CsrfTokenResponse> csrf(
			HttpServletRequest servletRequest) {
		Object attribute = servletRequest.getAttribute(
				DeferredCsrfToken.class.getName());
		if (!(attribute instanceof DeferredCsrfToken deferredCsrfToken)) {
			throw new IllegalStateException(
					"CSRF token is unavailable for this request");
		}
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(CsrfTokenResponse.from(deferredCsrfToken.get()));
	}

	@PostMapping("/login")
	@Operation(
			operationId = "login",
			summary = "Create a browser session",
			description = """
					Authenticates an active verified account, opens one device session, \
					and writes short-lived access and rotating refresh credentials to \
					HttpOnly cookies. Tokens never appear in the response body.
					""")
	@SecurityRequirement(name = "csrfHeader")
	@ApiResponses({
			@ApiResponse(
					responseCode = "200",
					description = "Authenticated session created",
					content = @Content(
							mediaType = MediaType.APPLICATION_JSON_VALUE,
							schema = @Schema(
									implementation =
											AuthenticatedSessionResponse.class))),
			@ApiResponse(
					responseCode = "401",
					description = "Credentials or account state are not eligible",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(
					responseCode = "403",
					description = "CSRF token is missing or invalid",
					content = @Content(
							mediaType = MediaType.APPLICATION_PROBLEM_JSON_VALUE,
							schema = @Schema(implementation = ProblemDetail.class)))
	})
	public ResponseEntity<AuthenticatedSessionResponse> login(
			@Valid @RequestBody LoginRequest request,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		Instant authenticatedAt = clock.instant();
		String clientAddress = clientAddressResolver.resolve(servletRequest);
		rateLimitService.check(
				AuthRateLimitScope.LOGIN,
				clientAddress,
				authenticatedAt);
		String deviceId = cookieService.resolveOrCreateDeviceId(servletRequest);
		IssuedBrowserSession session = authenticationService.login(
				request.email(),
				request.password(),
				deviceId,
				request.deviceName(),
				userAgent(servletRequest),
				clientAddress,
				authenticatedAt);
		cookieService.writeAuthenticatedSession(
				servletResponse,
				session,
				deviceId);
		cookieService.rotateCsrf(servletRequest, servletResponse);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(AuthenticatedSessionResponse.from(session));
	}

	@PostMapping("/refresh")
	@Operation(
			operationId = "refreshSession",
			summary = "Rotate the browser refresh credential",
			description = """
					Consumes the current refresh cookie atomically and replaces both \
					authentication cookies. Reuse compromises only this device session.
					""")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<AuthenticatedSessionResponse> refresh(
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		try {
			Instant refreshedAt = clock.instant();
			String clientAddress =
					clientAddressResolver.resolve(servletRequest);
			rateLimitService.check(
					AuthRateLimitScope.REFRESH,
					clientAddress,
					refreshedAt);
			String rawRefreshToken = cookieService
					.refreshToken(servletRequest)
					.orElseThrow(InvalidRefreshTokenException::new);
			String deviceId = cookieService.resolveOrCreateDeviceId(
					servletRequest);
			IssuedBrowserSession session = authenticationService.refresh(
					rawRefreshToken,
					clientAddress,
					userAgent(servletRequest),
					refreshedAt);
			cookieService.writeAuthenticatedSession(
					servletResponse,
					session,
					deviceId);
			cookieService.rotateCsrf(servletRequest, servletResponse);
			return ResponseEntity.ok()
					.cacheControl(CacheControl.noStore())
					.body(AuthenticatedSessionResponse.from(session));
		}
		catch (InvalidRefreshTokenException invalidRefreshToken) {
			cookieService.clearAuthentication(servletResponse);
			cookieService.rotateCsrf(servletRequest, servletResponse);
			throw invalidRefreshToken;
		}
	}

	@PostMapping("/logout")
	@Operation(
			operationId = "logoutCurrentSession",
			summary = "Log out the current device")
	@SecurityRequirement(name = "cookieAuth")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<Void> logout(
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		authenticationService.logoutCurrent(
				UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(
						SessionBoundJwtValidator.SESSION_ID_CLAIM)),
				clock.instant());
		cookieService.clearAuthentication(servletResponse);
		cookieService.rotateCsrf(servletRequest, servletResponse);
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}

	@PostMapping("/logout-all")
	@Operation(
			operationId = "logoutEverywhere",
			summary = "Log out every active device")
	@SecurityRequirement(name = "cookieAuth")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<Void> logoutEverywhere(
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		authenticationService.logoutEverywhere(
				UUID.fromString(jwt.getSubject()),
				clock.instant());
		cookieService.clearAuthentication(servletResponse);
		cookieService.rotateCsrf(servletRequest, servletResponse);
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}

	private static String userAgent(HttpServletRequest request) {
		String userAgent = request.getHeader("User-Agent");
		if (userAgent == null) {
			return null;
		}
		String stripped = userAgent.strip();
		if (stripped.isEmpty()) {
			return null;
		}
		return stripped.length() <= MAXIMUM_USER_AGENT_LENGTH
				? stripped
				: stripped.substring(0, MAXIMUM_USER_AGENT_LENGTH);
	}
}
