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
import org.springframework.web.bind.annotation.RestController;

import com.sahha.organisation.dto.response.SchedulingDoctorResponse;
import com.sahha.organisation.exception.OrganisationAccessDeniedException;
import com.sahha.organisation.service.schedulingdirectoryservice.SchedulingDirectoryService;

@RestController
@RequestMapping("/api/v1/organisations/{organisationId}/scheduling-doctors")
@SecurityRequirement(name = "cookieAuth")
@Tag(
		name = "Scheduling doctor directory",
		description = "Minimal active-doctor validation for authorised appointment operations.")
public class SchedulingDirectoryController {

	private final SchedulingDirectoryService schedulingDirectoryService;

	public SchedulingDirectoryController(
			SchedulingDirectoryService schedulingDirectoryService) {
		this.schedulingDirectoryService = schedulingDirectoryService;
	}

	@GetMapping("/{doctorUserId}")
	@Operation(
			operationId = "getActiveSchedulingDoctor",
			summary = "Resolve one active doctor for appointment scheduling")
	public ResponseEntity<SchedulingDoctorResponse> find(
			@PathVariable UUID organisationId,
			@PathVariable UUID doctorUserId,
			@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok()
				.cacheControl(CacheControl.noStore())
				.body(schedulingDirectoryService.findActiveDoctor(
						organisationId,
						doctorUserId,
						actorUserId(jwt)));
	}

	private static UUID actorUserId(Jwt jwt) {
		try {
			return UUID.fromString(jwt.getSubject());
		}
		catch (RuntimeException invalidSubject) {
			throw new OrganisationAccessDeniedException();
		}
	}
}
