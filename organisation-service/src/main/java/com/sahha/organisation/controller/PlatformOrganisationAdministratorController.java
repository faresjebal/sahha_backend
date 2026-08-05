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
import com.sahha.organisation.dto.request.AssignOrganisationAdministratorRequest;
import com.sahha.organisation.dto.response.OrganisationMembershipPageResponse;
import com.sahha.organisation.dto.response.OrganisationMembershipResponse;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationAdministratorAssignmentService;
import com.sahha.organisation.service.organisationmembershipservice.OrganisationMembershipService;

@RestController
@RequestMapping("/api/v1/platform/organisations/{organisationId}/administrators")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Platform organisation administrators",
		description = "Platform-controlled Organisation Administrator membership assignment.")
public class PlatformOrganisationAdministratorController {

	private final OrganisationAdministratorAssignmentService assignmentService;
	private final OrganisationMembershipService membershipService;

	public PlatformOrganisationAdministratorController(
			OrganisationAdministratorAssignmentService assignmentService,
			OrganisationMembershipService membershipService) {
		this.assignmentService = assignmentService;
		this.membershipService = membershipService;
	}

	@PostMapping
	@SecurityRequirement(name = "csrfHeader")
	@Operation(
			operationId = "assignOrganisationAdministrator",
			summary = "Assign an active verified Sahha user as Organisation Administrator")
	public ResponseEntity<OrganisationMembershipResponse> assign(
			@PathVariable UUID organisationId,
			@Valid @RequestBody AssignOrganisationAdministratorRequest request,
			@AuthenticationPrincipal Jwt jwt,
			HttpServletRequest httpRequest) {
		OrganisationMembershipResponse created = assignmentService.assign(
				organisationId,
				request,
				UUID.fromString(jwt.getSubject()),
				jwt.getTokenValue(),
				RequestIdFilter.requestId(httpRequest));
		return ResponseEntity.created(URI.create(
					"/api/v1/platform/organisations/" + organisationId
							+ "/administrators/" + created.id()))
				.cacheControl(CacheControl.noStore())
				.body(created);
	}

	@GetMapping
	@Operation(
			operationId = "listOrganisationAdministrators",
			summary = "List Organisation Administrator memberships")
	public ResponseEntity<OrganisationMembershipPageResponse> list(
			@PathVariable UUID organisationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(membershipService.listAdministrators(
						organisationId,
						page,
						size));
	}

	@GetMapping("/{membershipId}")
	@Operation(
			operationId = "getOrganisationAdministrator",
			summary = "Read one Organisation Administrator membership")
	public ResponseEntity<OrganisationMembershipResponse> find(
			@PathVariable UUID organisationId,
			@PathVariable UUID membershipId) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(membershipService.findAdministrator(
						organisationId,
						membershipId));
	}
}
