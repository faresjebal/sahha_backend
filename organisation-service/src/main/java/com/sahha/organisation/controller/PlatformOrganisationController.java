package com.sahha.organisation.controller;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.config.RequestIdFilter;
import com.sahha.organisation.dto.request.CreateOrganisationRequest;
import com.sahha.organisation.dto.response.OrganisationPageResponse;
import com.sahha.organisation.dto.response.OrganisationResponse;
import com.sahha.organisation.service.organisationservice.OrganisationService;

@RestController
@RequestMapping("/api/v1/platform/organisations")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Platform organisations",
		description = "Platform-administrator healthcare organisation controls.")
public class PlatformOrganisationController {

	private final OrganisationService organisationService;

	public PlatformOrganisationController(
			OrganisationService organisationService) {
		this.organisationService = organisationService;
	}

	@PostMapping
	@Operation(
			operationId = "createOrganisation",
			summary = "Create an active healthcare organisation",
			description = """
					Creates an organisation directly as ACTIVE. Only a global \
					Platform Administrator may use this internship endpoint.
					""")
	@SecurityRequirement(name = "csrfHeader")
	public ResponseEntity<OrganisationResponse> create(
			@Valid @RequestBody CreateOrganisationRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		OrganisationResponse created = organisationService.create(
				request,
				UUID.fromString(jwt.getSubject()),
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(
						URI.create(
								"/api/v1/platform/organisations/"
										+ created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@GetMapping
	@Operation(
			operationId = "listOrganisations",
			summary = "List healthcare organisations")
	public ResponseEntity<OrganisationPageResponse> list(
			@RequestParam(defaultValue = "0")
			int page,
			@RequestParam(defaultValue = "20")
			int size) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(organisationService.list(page, size));
	}

	@GetMapping("/{organisationId}")
	@Operation(
			operationId = "getOrganisation",
			summary = "Read one healthcare organisation")
	public ResponseEntity<OrganisationResponse> find(
			@PathVariable UUID organisationId) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(organisationService.find(organisationId));
	}
}
