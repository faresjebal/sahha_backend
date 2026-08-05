package com.sahha.auth.controller;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.dto.request.ChangePasswordRequest;
import com.sahha.auth.dto.response.ActiveSessionResponse;
import com.sahha.auth.dto.response.CurrentSessionResponse;
import com.sahha.auth.dto.response.CurrentAccountResponse;
import com.sahha.auth.exception.OwnedSessionNotFoundException;
import com.sahha.auth.security.SessionBoundJwtValidator;
import com.sahha.auth.service.browsersessionservice.BrowserSessionCookieService;
import com.sahha.auth.service.useraccountservice.AccountSecurityService;
import com.sahha.auth.service.usersessionservice.UserSessionService;

@RestController
@RequestMapping("/api/v1/auth")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Account security",
		description = "Authenticated password and device-session controls.")
public class AccountSecurityController {

	private final UserSessionService sessionService;
	private final AccountSecurityService accountSecurityService;
	private final BrowserSessionCookieService cookieService;
	private final Clock clock;

	public AccountSecurityController(
			UserSessionService sessionService,
			AccountSecurityService accountSecurityService,
			BrowserSessionCookieService cookieService,
			Clock clock) {
		this.sessionService = sessionService;
		this.accountSecurityService = accountSecurityService;
		this.cookieService = cookieService;
		this.clock = clock;
	}

	@GetMapping("/sessions")
	@Operation(
			operationId = "listActiveSessions",
			summary = "List the current account's active device sessions")
	public ResponseEntity<List<ActiveSessionResponse>> sessions(
			@AuthenticationPrincipal Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UUID currentSessionId = sessionId(jwt);
		List<ActiveSessionResponse> sessions = sessionService
				.listActiveSessions(
						userId,
						currentSessionId,
						clock.instant())
				.stream()
				.map(ActiveSessionResponse::from)
				.toList();
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(sessions);
	}

	@GetMapping("/session")
	@Operation(
			operationId = "getCurrentSession",
			summary = "Restore the current authenticated browser session",
			description = """
					Returns safe metadata from the already validated access cookie \
					without issuing or rotating an access or refresh credential.
					""")
	public ResponseEntity<CurrentSessionResponse> currentSession(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(CurrentSessionResponse.from(jwt));
	}

	@GetMapping("/account")
	@Operation(
			operationId = "getCurrentAccount",
			summary = "Read the current account's authoritative identity",
			description = "Returns only the authenticated caller's account identity and verification state.")
	public ResponseEntity<CurrentAccountResponse> currentAccount(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(accountSecurityService.currentAccount(
						UUID.fromString(jwt.getSubject())));
	}

	@DeleteMapping("/sessions/{sessionId}")
	@Operation(
			operationId = "revokeOwnedSession",
			summary = "Revoke one device session owned by the current account")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<Void> revokeSession(
			@PathVariable UUID sessionId,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest request,
			HttpServletResponse response) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UUID currentSessionId = sessionId(jwt);
		if (!sessionService.revokeOwnedSession(
				userId,
				sessionId,
				clock.instant())) {
			throw new OwnedSessionNotFoundException();
		}
		if (currentSessionId.equals(sessionId)) {
			cookieService.clearAuthentication(response);
			cookieService.rotateCsrf(request, response);
		}
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}

	@PostMapping("/password-change")
	@Operation(
			operationId = "changePassword",
			summary = "Change the authenticated account password",
			description = "Requires the current password and revokes every session.")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<Void> changePassword(
			@Valid @RequestBody ChangePasswordRequest passwordRequest,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest request,
			HttpServletResponse response) {
		accountSecurityService.changePassword(
				UUID.fromString(jwt.getSubject()),
				passwordRequest.currentPassword(),
				passwordRequest.newPassword(),
				clock.instant());
		cookieService.clearAuthentication(response);
		cookieService.rotateCsrf(request, response);
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}

	private static UUID sessionId(Jwt jwt) {
		return UUID.fromString(jwt.getClaimAsString(
				SessionBoundJwtValidator.SESSION_ID_CLAIM));
	}
}
