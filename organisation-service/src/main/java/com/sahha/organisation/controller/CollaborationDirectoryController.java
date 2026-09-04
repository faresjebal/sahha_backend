package com.sahha.organisation.controller;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.dto.response.CollaborationDoctorPageResponse;
import com.sahha.organisation.dto.response.SchedulingDoctorResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.service.collaborationdirectoryservice.CollaborationDirectoryService;

@RestController
@RequestMapping("/api/v1/organisations/{organisationId}/collaboration-doctors")
@SecurityRequirement(name = "cookieAuth")
@Tag(name = "Doctor collaboration directory",
		description = "Minimal active-doctor identities for secure collaboration.")
public class CollaborationDirectoryController {

	private final CollaborationDirectoryService service;

	public CollaborationDirectoryController(CollaborationDirectoryService service) {
		this.service = service;
	}

	@GetMapping
	@Operation(operationId = "listCollaborationDoctors",
			summary = "List active doctors available in the active organisation")
	public ResponseEntity<CollaborationDoctorPageResponse> list(
			@PathVariable UUID organisationId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "100") int size,
			@AuthenticationPrincipal Jwt jwt) {
		requireActiveOrganisation(organisationId, jwt);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.list(organisationId, actorUserId(jwt), page, size));
	}

	@GetMapping("/{doctorUserId}")
	@Operation(operationId = "getCollaborationDoctor",
			summary = "Resolve one active doctor for secure collaboration")
	public ResponseEntity<SchedulingDoctorResponse> find(
			@PathVariable UUID organisationId,
			@PathVariable UUID doctorUserId,
			@AuthenticationPrincipal Jwt jwt) {
		requireActiveOrganisation(organisationId, jwt);
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(service.find(organisationId, doctorUserId, actorUserId(jwt)));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalid) {
			throw new OrganisationAccessDeniedException();
		}
	}

	private static void requireActiveOrganisation(UUID organisationId, Jwt jwt) {
		try {
			if (!organisationId.equals(UUID.fromString(
					jwt.getClaimAsString("org_id")))) {
				throw new OrganisationAccessDeniedException();
			}
		}
		catch (IllegalArgumentException | NullPointerException invalid) {
			throw new OrganisationAccessDeniedException();
		}
	}
}
