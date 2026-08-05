package com.sahha.auth.controller;

import java.time.Clock;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.dto.request.SelectActiveOrganisationRequest;
import com.sahha.auth.dto.response.ActiveOrganisationSessionResponse;
import com.sahha.auth.security.SessionBoundJwtValidator;
import com.sahha.auth.service.browsersessionservice.BrowserSessionCookieService;
import com.sahha.auth.service.usersessionservice.ActiveOrganisationSelectionService;
import com.sahha.auth.service.usersessionservice.IssuedActiveOrganisationSession;

@RestController
@RequestMapping("/api/v1/auth/active-organisation")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Browser sessions",
		description = "Cookie-authenticated browser session lifecycle.")
public class ActiveOrganisationController {

	private final ActiveOrganisationSelectionService selectionService;
	private final BrowserSessionCookieService cookieService;
	private final Clock clock;

	public ActiveOrganisationController(
			ActiveOrganisationSelectionService selectionService,
			BrowserSessionCookieService cookieService,
			Clock clock) {
		this.selectionService = selectionService;
		this.cookieService = cookieService;
		this.clock = clock;
	}

	@PostMapping
	@SecurityRequirement(name = "csrfHeader")
	@Operation(
			operationId = "selectActiveOrganisation",
			summary = "Select an organisation for the current browser session",
			description = """
					Organisation Service first validates the current user's active membership. \
					Auth then updates only this device session and replaces only the access \
					cookie; the refresh-token family is not rotated.
					""")
	public ResponseEntity<ActiveOrganisationSessionResponse> select(
			@Valid @RequestBody SelectActiveOrganisationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest servletRequest,
			HttpServletResponse servletResponse) {
		IssuedActiveOrganisationSession session = selectionService.select(
				UUID.fromString(jwt.getSubject()),
				UUID.fromString(jwt.getClaimAsString(
						SessionBoundJwtValidator.SESSION_ID_CLAIM)),
				request.organisationId(),
				jwt.getTokenValue(),
				clock.instant());
		cookieService.writeAccessToken(
				servletResponse,
				session.getAccessToken());
		cookieService.rotateCsrf(servletRequest, servletResponse);
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(ActiveOrganisationSessionResponse.from(session));
	}
}
