package com.sahha.auth.controller;

import java.time.Clock;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.auth.dto.request.AccountStatusUpdateRequest;
import com.sahha.auth.service.useraccountservice.AccountAdministrationService;

@RestController
@RequestMapping("/api/v1/auth/platform/accounts")
@SecurityRequirement(name = "cookieAuth")
@SecurityRequirement(name = "csrfHeader")
@Tag(
		name = "Platform account administration",
		description = "Platform-administrator hooks for authentication accounts.")
public class AccountAdministrationController {

	private final AccountAdministrationService administrationService;
	private final Clock clock;

	public AccountAdministrationController(
			AccountAdministrationService administrationService,
			Clock clock) {
		this.administrationService = administrationService;
		this.clock = clock;
	}

	@PutMapping("/{userId}/status")
	@Operation(
			operationId = "updateAccountStatus",
			summary = "Suspend, reactivate, or disable an authentication account")
	public ResponseEntity<Void> updateStatus(
			@PathVariable UUID userId,
			@Valid @RequestBody AccountStatusUpdateRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		administrationService.updateStatus(
				UUID.fromString(jwt.getSubject()),
				userId,
				request.action(),
				clock.instant());
		return ResponseEntity.noContent()
				.cacheControl(CacheControl.noStore())
				.build();
	}
}
