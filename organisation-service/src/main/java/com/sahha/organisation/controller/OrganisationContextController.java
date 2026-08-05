package com.sahha.organisation.controller;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.dto.response.OrganisationContextResponse;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationContextService;

@RestController
@RequestMapping("/api/v1/organisations")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Organisation context",
		description = "Authenticated, user-owned organisation workspace contexts.")
public class OrganisationContextController {

	private final OrganisationContextService contextService;

	public OrganisationContextController(
			OrganisationContextService contextService) {
		this.contextService = contextService;
	}

	@GetMapping("/memberships")
	@Operation(
			operationId = "listMyOrganisationContexts",
			summary = "List active organisation contexts for the current user")
	public ResponseEntity<List<OrganisationContextResponse>> list(
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(contextService.listAvailable(
						UUID.fromString(jwt.getSubject())));
	}

	@GetMapping("/{organisationId}/membership-context")
	@Operation(
			operationId = "resolveMyOrganisationContext",
			summary = "Resolve one active organisation context for the current user")
	public ResponseEntity<OrganisationContextResponse> resolve(
			@PathVariable UUID organisationId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(contextService.resolve(
						organisationId,
						UUID.fromString(jwt.getSubject())));
	}
}
